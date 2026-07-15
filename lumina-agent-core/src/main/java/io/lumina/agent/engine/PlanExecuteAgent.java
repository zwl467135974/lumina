package io.lumina.agent.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.lumina.agent.model.AgentConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plan-Execute Agent（引擎层组合实现）
 *
 * <p>AgentScope 1.0.7 无 PlanAgent，本类用两个 ReActAgent 组合实现：
 * <ol>
 *   <li>Plan 阶段：Planner（无工具，输出 JSON 子任务列表）</li>
 *   <li>Execute 阶段：Executor（带工具，逐个执行子任务）</li>
 *   <li>Summarize 阶段：汇总各步结果生成最终答复</li>
 * </ol>
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Slf4j
public class PlanExecuteAgent {

    private static final String PLANNER_PROMPT = """
            You are a task planning assistant. Break down the user's request into 1-5 actionable sub-tasks.
            Output ONLY a JSON array: [{"step": "description"}, ...]
            No explanations, no markdown fences.
            """;

    private static final String EXECUTOR_PROMPT = """
            You are a task execution agent. Complete the given sub-task using available tools.
            Report the result clearly and concisely.
            """;

    private static final String SUMMARIZER_PROMPT = """
            You are a summarization assistant. Synthesize the execution results of multiple sub-tasks
            into a coherent final answer for the user.
            """;

    private static final int MAX_SUBTASKS = 10;

    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[.*]", Pattern.DOTALL);

    private final Model model;
    private final Toolkit toolkit;
    private final String userPrompt;
    private final String systemPrompt;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private long totalInputTokens = 0;
    private long totalOutputTokens = 0;

    public PlanExecuteAgent(Model model, Toolkit toolkit, String userPrompt, String systemPrompt) {
        this.model = model;
        this.toolkit = toolkit;
        this.userPrompt = userPrompt;
        this.systemPrompt = systemPrompt != null ? systemPrompt : "You are a helpful AI assistant.";
    }

    /**
     * 执行 Plan-Execute 流程
     *
     * @return 最终汇总回复
     */
    public Msg execute() {
        // 1. Plan 阶段
        log.info("Plan-Execute: Plan 阶段开始");
        List<String> subTasks = plan();
        log.info("Plan-Execute: 分解出 {} 个子任务", subTasks.size());

        if (subTasks.isEmpty()) {
            // 规划失败，直接用原 prompt 执行
            return executeDirect();
        }

        // 2. Execute 阶段
        List<String> results = new ArrayList<>();
        for (int i = 0; i < subTasks.size(); i++) {
            String task = subTasks.get(i);
            log.info("Plan-Execute: 执行子任务 {}/{}: {}", i + 1, subTasks.size(), task);
            String result = executeSubTask(task, i, subTasks, results);
            results.add(result);
        }

        // 3. Summarize 阶段
        log.info("Plan-Execute: Summarize 阶段");
        Msg summary = summarize(subTasks, results);
        log.info("Plan-Execute: 完成, inputTokens={}, outputTokens={}", totalInputTokens, totalOutputTokens);
        return summary;
    }

    /**
     * Plan 阶段：Planner 分解任务
     */
    private List<String> plan() {
        ReActAgent planner = ReActAgent.builder()
                .name("Planner")
                .sysPrompt(PLANNER_PROMPT + "\n\nContext: " + systemPrompt)
                .model(model)
                .toolkit(new Toolkit())  // 空工具集
                .memory(new io.agentscope.core.memory.InMemoryMemory())
                .build();

        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(
                "Break down this request into sub-tasks:\n" + userPrompt).build();

        Msg response = planner.call(List.of(userMsg)).block();
        if (response == null || response.getTextContent() == null) {
            log.warn("Plan-Execute: Planner 返回空响应");
            return List.of();
        }
        accumulateTokens(response.getChatUsage());

        return parseSubTasks(response.getTextContent());
    }

    /**
     * Execute 阶段：Executor 执行单个子任务
     */
    private String executeSubTask(String task, int index, List<String> allTasks, List<String> prevResults) {
        ReActAgent executor = ReActAgent.builder()
                .name("Executor-" + (index + 1))
                .sysPrompt(EXECUTOR_PROMPT)
                .model(model)
                .toolkit(toolkit)
                .memory(new io.agentscope.core.memory.InMemoryMemory())
                .build();

        StringBuilder prompt = new StringBuilder();
        prompt.append("Sub-task ").append(index + 1).append(": ").append(task);

        // 附带前序结果作为上下文
        if (!prevResults.isEmpty()) {
            prompt.append("\n\nPrevious results:\n");
            for (int i = 0; i < prevResults.size(); i++) {
                prompt.append("Step ").append(i + 1).append(" result: ").append(prevResults.get(i)).append("\n");
            }
        }

        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(prompt.toString()).build();
        Msg response = executor.call(List.of(userMsg)).block();
        if (response == null || response.getTextContent() == null) {
            return "(no result)";
        }
        accumulateTokens(response.getChatUsage());
        return response.getTextContent();
    }

    /**
     * Summarize 阶段：汇总各步结果
     */
    private Msg summarize(List<String> subTasks, List<String> results) {
        ReActAgent summarizer = ReActAgent.builder()
                .name("Summarizer")
                .sysPrompt(SUMMARIZER_PROMPT + "\n\nOriginal context: " + systemPrompt)
                .model(model)
                .toolkit(new Toolkit())
                .memory(new io.agentscope.core.memory.InMemoryMemory())
                .build();

        StringBuilder prompt = new StringBuilder();
        prompt.append("Original request: ").append(userPrompt).append("\n\n");
        prompt.append("Sub-task execution results:\n");
        for (int i = 0; i < subTasks.size(); i++) {
            prompt.append("Step ").append(i + 1).append(" [").append(subTasks.get(i)).append("]:\n");
            prompt.append(results.get(i)).append("\n\n");
        }
        prompt.append("Please synthesize these results into a final answer.");

        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(prompt.toString()).build();
        Msg response = summarizer.call(List.of(userMsg)).block();
        if (response == null) {
            // 降级：拼接所有结果
            String fallback = String.join("\n\n", results);
            return Msg.builder().role(MsgRole.ASSISTANT).textContent(fallback).build();
        }
        accumulateTokens(response.getChatUsage());
        return response;
    }

    /**
     * 规划失败降级：直接执行（等同普通 ReAct）
     */
    private Msg executeDirect() {
        log.warn("Plan-Execute: 规划失败，降级为直接执行");
        ReActAgent agent = ReActAgent.builder()
                .name("FallbackAgent")
                .sysPrompt(systemPrompt)
                .model(model)
                .toolkit(toolkit)
                .memory(new io.agentscope.core.memory.InMemoryMemory())
                .build();

        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(userPrompt).build();
        Msg response = agent.call(List.of(userMsg)).block();
        accumulateTokens(response != null ? response.getChatUsage() : null);
        return response != null ? response
                : Msg.builder().role(MsgRole.ASSISTANT).textContent("执行失败").build();
    }

    /**
     * 解析 Planner 输出的 JSON 子任务列表
     */
    private List<String> parseSubTasks(String text) {
        // 提取 JSON 数组部分
        Matcher matcher = JSON_ARRAY_PATTERN.matcher(text);
        if (!matcher.find()) {
            log.warn("Plan-Execute: Planner 输出未找到 JSON 数组: {}", text.substring(0, Math.min(200, text.length())));
            return List.of();
        }

        String json = matcher.group();
        try {
            List<Map<String, Object>> items = objectMapper.readValue(json, new TypeReference<>() {});
            List<String> tasks = new ArrayList<>();
            for (Map<String, Object> item : items) {
                Object step = item.get("step");
                if (step != null) {
                    tasks.add(step.toString());
                }
                if (tasks.size() >= MAX_SUBTASKS) {
                    break;
                }
            }
            return tasks;
        } catch (Exception e) {
            log.warn("Plan-Execute: 解析子任务 JSON 失败: {}", e.getMessage());
            return List.of();
        }
    }

    private void accumulateTokens(ChatUsage usage) {
        if (usage != null) {
            totalInputTokens += usage.getInputTokens();
            totalOutputTokens += usage.getOutputTokens();
        }
    }

    public long getTotalInputTokens() {
        return totalInputTokens;
    }

    public long getTotalOutputTokens() {
        return totalOutputTokens;
    }
}
