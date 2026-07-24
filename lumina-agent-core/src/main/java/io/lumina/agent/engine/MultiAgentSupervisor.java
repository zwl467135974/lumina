package io.lumina.agent.engine;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 多 Agent 协作——Supervisor（主管）模式
 *
 * <p>一个 Supervisor LLM 充当路由器，接收用户请求后判断交给哪个专家 Agent 处理。
 * 专家处理完后结果回传给 Supervisor，Supervisor 决定是否需要更多专家或直接回复用户。
 *
 * <p>流程：
 * <ol>
 *   <li>Supervisor 分析用户请求 + 专家列表 → 输出选择的专家名（或 FINISH）</li>
 *   <li>被选中的专家执行 → 结果回传给 Supervisor</li>
 *   <li>Supervisor 再次分析（带上之前的专家结果）→ 选下一个或 FINISH</li>
 *   <li>FINISH 时 Supervisor 汇总所有结果 → 最终回复</li>
 * </ol>
 *
 * <p>类似 OpenAI Swarm 的 handoff 模式，但由 Supervisor 集中调度而非链式传递。
 *
 * @author Lumina Team
 * @since 3.8.0
 */
@Slf4j
public class MultiAgentSupervisor {

    private final Model supervisorModel;
    private final List<SubAgentSpec> subAgents;
    private final String supervisorPrompt;
    private final int maxRounds;

    /** Trace 采集器（可选） */
    private io.lumina.agent.tracing.TraceCollector traceCollector;

    private long totalInputTokens = 0;
    private long totalOutputTokens = 0;

    private static final int DEFAULT_MAX_ROUNDS = 5;

    private static final String SUPERVISOR_SYSTEM_PROMPT = """
            你是一个任务主管（Supervisor），负责将用户请求分配给合适的专家处理。

            可用的专家：
            %s

            工作规则：
            1. 分析用户请求，选择最合适的专家
            2. 只输出专家名称（如"数据分析专家"），或输出"FINISH"表示任务完成
            3. 如果已有专家结果，判断是否需要其他专家补充
            4. 输出 FINISH 后，在下一轮给出最终汇总回复
            只输出专家名称或 FINISH，不要输出其他内容。
            """;

    /**
     * 已解析的子 Agent 规格（引擎层构建后传入）
     */
    public record SubAgentSpec(String name, String description, String sysPrompt,
                                Model model, Toolkit toolkit) {}

    public MultiAgentSupervisor(Model supervisorModel, List<SubAgentSpec> subAgents,
                                 String supervisorPrompt, int maxRounds) {
        this.supervisorModel = supervisorModel;
        this.subAgents = subAgents;
        this.supervisorPrompt = supervisorPrompt;
        this.maxRounds = maxRounds > 0 ? maxRounds : DEFAULT_MAX_ROUNDS;
    }

    public void setTraceCollector(io.lumina.agent.tracing.TraceCollector traceCollector) {
        this.traceCollector = traceCollector;
    }

    /**
     * 执行多 Agent 协作
     *
     * @param messages 上下文消息（含用户请求）
     * @return 最终汇总回复
     */
    public Msg execute(List<Msg> messages) {
        // 构建专家列表描述
        StringBuilder expertList = new StringBuilder();
        for (SubAgentSpec spec : subAgents) {
            expertList.append("- ").append(spec.name())
                    .append(": ").append(spec.description() != null ? spec.description() : "")
                    .append("\n");
        }

        String systemPrompt = String.format(SUPERVISOR_SYSTEM_PROMPT, expertList.toString());
        if (supervisorPrompt != null && !supervisorPrompt.isBlank()) {
            systemPrompt = supervisorPrompt + "\n\n" + systemPrompt;
        }

        // Supervisor Agent（空工具集，只做路由判断）
        ReActAgent supervisor = ReActAgent.builder()
                .name("Supervisor")
                .sysPrompt(systemPrompt)
                .model(supervisorModel)
                .toolkit(new Toolkit())
                .build();

        // 对话上下文（包含用户请求 + 专家结果历史）
        List<Msg> context = new ArrayList<>(messages);
        StringBuilder expertResults = new StringBuilder();

        log.info("MultiAgent Supervisor 启动: {} 个专家, maxRounds={}", subAgents.size(), maxRounds);

        for (int round = 0; round < maxRounds; round++) {
            // 1. Supervisor 判断下一步
            Msg routeMsg = Msg.builder().role(MsgRole.USER)
                    .textContent(buildRoutePrompt(context, expertResults.toString(), round))
                    .build();

            Msg routeResponse = supervisor.call(List.of(routeMsg)).block();
            if (routeResponse == null) {
                log.warn("Supervisor 路由返回空，终止");
                break;
            }
            accumulateTokens(routeResponse.getChatUsage());

            String decision = routeResponse.getTextContent() != null
                    ? routeResponse.getTextContent().trim() : "";

            log.info("Supervisor 第 {} 轮决策: {}", round + 1, decision);

            // 2. 判断是否完成
            if ("FINISH".equalsIgnoreCase(decision) || decision.toUpperCase().contains("FINISH")) {
                // Supervisor 给出最终汇总
                return generateFinalSummary(supervisor, context, expertResults.toString());
            }

            // 3. 查找匹配的专家
            SubAgentSpec selected = findAgent(decision);
            if (selected == null) {
                log.warn("未找到匹配的专家: {}，直接汇总", decision);
                return generateFinalSummary(supervisor, context, expertResults.toString());
            }

            // 4. 执行专家
            log.info("调用专家: {}", selected.name());
            ReActAgent expertAgent = ReActAgent.builder()
                    .name(selected.name())
                    .sysPrompt(selected.sysPrompt() != null ? selected.sysPrompt() : "You are a helpful assistant.")
                    .model(selected.model())
                    .toolkit(selected.toolkit() != null ? selected.toolkit() : new Toolkit())
                    .build();

            Msg expertTask = Msg.builder().role(MsgRole.USER)
                    .textContent(extractUserTask(context))
                    .build();

            Msg expertResponse = expertAgent.call(List.of(expertTask)).block();
            if (expertResponse != null) {
                accumulateTokens(expertResponse.getChatUsage());
                String result = expertResponse.getTextContent() != null
                        ? expertResponse.getTextContent() : "";
                expertResults.append("【").append(selected.name()).append("的结果】\n")
                        .append(result).append("\n\n");
                log.info("专家 {} 完成, 结果长度: {}", selected.name(), result.length());
            }
        }

        // 超过最大轮次，强制汇总
        log.warn("Supervisor 达到最大轮次 {}, 强制汇总", maxRounds);
        return generateFinalSummary(supervisor, context, expertResults.toString());
    }

    /**
     * 构建路由判断 Prompt
     */
    private String buildRoutePrompt(List<Msg> context, String expertResults, int round) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户原始请求: ").append(extractUserTask(context)).append("\n\n");

        if (!expertResults.isBlank()) {
            prompt.append("已完成的专家结果:\n").append(expertResults).append("\n");
        }

        prompt.append("请判断下一步交给哪个专家，或输出 FINISH。");
        return prompt.toString();
    }

    /**
     * Supervisor 生成最终汇总
     *
     * <p>使用独立的 Summarizer Agent（不受路由 Prompt 约束），
     * 避免 Supervisor 的"只输出专家名"指令影响汇总质量。
     */
    private Msg generateFinalSummary(ReActAgent supervisor, List<Msg> context, String expertResults) {
        if (expertResults.isBlank()) {
            return Msg.builder().role(MsgRole.ASSISTANT)
                    .textContent("没有专家被调用，无法处理请求。")
                    .build();
        }

        // 独立的汇总 Agent（System Prompt 鼓励完整回复，不受路由约束）
        ReActAgent summarizer = ReActAgent.builder()
                .name("Summarizer")
                .sysPrompt("你是一个汇总助手。根据各专家的处理结果，给出完整、连贯的最终回复。直接回复用户，不要输出路由信息。")
                .model(supervisorModel)
                .toolkit(new Toolkit())
                .build();

        String summaryPrompt = "用户请求: " + extractUserTask(context) +
                "\n\n以下是各专家的处理结果:\n" + expertResults +
                "\n请基于以上结果，给出最终汇总回复。";

        Msg summaryMsg = Msg.builder().role(MsgRole.USER).textContent(summaryPrompt).build();
        Msg response = summarizer.call(List.of(summaryMsg)).block();

        if (response == null) {
            return Msg.builder().role(MsgRole.ASSISTANT)
                    .textContent(expertResults)
                    .build();
        }
        accumulateTokens(response.getChatUsage());
        return response;
    }

    /**
     * 根据决策文本查找匹配的专家
     */
    private SubAgentSpec findAgent(String decision) {
        for (SubAgentSpec spec : subAgents) {
            if (decision.contains(spec.name()) || spec.name().contains(decision)) {
                return spec;
            }
        }
        return null;
    }

    /**
     * 从上下文中提取用户最新请求
     */
    private String extractUserTask(List<Msg> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg msg = messages.get(i);
            if (MsgRole.USER.equals(msg.getRole())) {
                return msg.getTextContent() != null ? msg.getTextContent() : "";
            }
        }
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1).getTextContent();
    }

    private void accumulateTokens(ChatUsage usage) {
        if (usage != null) {
            totalInputTokens += usage.getInputTokens();
            totalOutputTokens += usage.getOutputTokens();
        }
    }

    public long getTotalInputTokens() { return totalInputTokens; }
    public long getTotalOutputTokens() { return totalOutputTokens; }
}
