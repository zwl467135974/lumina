package io.lumina.agent.service.impl;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.agent.service.ContextSummarizer;
import io.lumina.agent.util.TokenEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩服务实现
 *
 * <p>两级压缩管线的第二级：调用 LLM 生成结构化检查点摘要，替代直接丢弃。
 * 参考 {@link ReflectiveMemoryServiceImpl} 的 LLM 调用模式。
 *
 * @author Lumina Team
 * @since 3.8.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextSummarizerImpl implements ContextSummarizer {

    private final ChatModelFactory chatModelFactory;
    private final LuminaAgentProperties agentProperties;

    private static final String LEGACY_SUMMARIZER_PROMPT = """
            你是对话摘要助手。将给定的对话历史压缩成简洁的摘要。

            规则：
            - 保留关键信息：用户意图、已做决策、重要数据、未解决的问题
            - 丢弃无关内容：寒暄、重复确认、情绪表达
            - 用第三人称客观描述，不要编造未提及的信息
            - 输出纯文本摘要，不要 JSON 或 Markdown 格式
            """;

    /** 检查点式压缩指令（8 段固定格式，借鉴 DeepSeek Harness compaction 提示词） */
    static final String CHECKPOINT_INSTRUCTION = """
            请把以上对话历史压缩为一份精确的检查点摘要，供后续对话继续任务时使用。
            按以下固定顺序输出 8 个段落，使用 Markdown 二级标题，没有内容的段落写 "(none)"：

            ## 主要请求与意图
            （用户原始目标及其演化，关键措辞逐字引用）

            ## 关键概念
            （涉及的技术/业务概念与约束）

            ## 文件与数据
            （精确的文件路径、数据标识、关键数值）

            ## 错误与修复
            （已出现的错误及解决方式，含用户的纠正意见）

            ## 未完成事项
            （尚待处理的工作）

            ## 当前工作
            （正在进行的具体事项）

            ## 下一步
            （单个明确的下一步行动）

            ## 关键上下文
            （已做出的决策及理由、用户偏好、开放问题）

            规则：
            - 简洁的工程散文，忠实于对话内容，不编造
            - 文件路径、命令、错误信息、标识符、数字、函数名必须逐字保留
            - 忠实记录用户对之前结果的纠正
            - 不要提及"这是摘要"或"上下文被压缩"
            - 只输出检查点文本
            """;

    /** 存在先前检查点时的合并规则（防多级压缩膨胀：合并不前拷） */
    static final String CHECKPOINT_MERGE_NOTE = """


            注意：以上对话的最前面已包含一份先前的检查点摘要。合并规则：
            保留仍然为真的事实，丢弃已被后续对话取代的过时内容，与新的对话内容
            合并为单一检查点输出，不要逐字照搬旧摘要的全文。
            """;

    private static final String FALLBACK_SYSTEM_PROMPT = "你是一个严谨的对话压缩助手。";

    @Override
    public String summarize(List<MemoryManager.Memory> olderMessages, String agentName) {
        if (olderMessages == null || olderMessages.isEmpty()) {
            return "";
        }

        try {
            // 构建对话文本
            StringBuilder dialogText = new StringBuilder();
            for (MemoryManager.Memory m : olderMessages) {
                String roleLabel = "assistant".equals(m.role()) ? "助手"
                        : "system".equals(m.role()) ? "系统" : "用户";
                dialogText.append(roleLabel).append(": ").append(m.content()).append("\n");
            }

            String prompt = "以下是 Agent「" + agentName + "」之前的对话历史，请生成摘要：\n\n"
                    + dialogText;

            Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(prompt).build();
            return callLlm(LEGACY_SUMMARIZER_PROMPT, List.of(userMsg));
        } catch (Exception e) {
            log.warn("上下文摘要生成失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String summarizeCheckpoint(List<MemoryManager.Memory> olderMessages,
                                      String conversationSystemPrompt, String previousSummary) {
        if (olderMessages == null || olderMessages.isEmpty()) {
            return null;
        }
        try {
            // 收缩保证前置：区域太小压缩无收益，直接放弃（调用方降级为确定性修剪）
            int sourceTokens = estimateSourceTokens(olderMessages, previousSummary);
            int summaryMaxTokens = agentProperties.getMemory().getCompression().getSummaryMaxTokens();
            if (sourceTokens <= summaryMaxTokens) {
                log.debug("压缩区域太小（{} tokens <= summaryMaxTokens），跳过检查点摘要", sourceTokens);
                return null;
            }

            boolean hasPrevious = previousSummary != null && !previousSummary.isBlank();

            // KV 前缀对齐：按原始角色重放被压缩消息，压缩指令作为最后一条 user 消息
            List<Msg> msgs = new ArrayList<>(olderMessages.size() + 2);
            if (hasPrevious) {
                msgs.add(Msg.builder().role(MsgRole.USER)
                        .textContent("[先前的检查点摘要]\n" + previousSummary).build());
            }
            for (MemoryManager.Memory m : olderMessages) {
                MsgRole role = "assistant".equals(m.role()) ? MsgRole.ASSISTANT
                        : "system".equals(m.role()) ? MsgRole.SYSTEM : MsgRole.USER;
                if (m.content() != null && !m.content().isBlank()) {
                    msgs.add(Msg.builder().role(role).textContent(m.content()).build());
                }
            }
            msgs.add(Msg.builder().role(MsgRole.USER)
                    .textContent(hasPrevious ? CHECKPOINT_INSTRUCTION + CHECKPOINT_MERGE_NOTE
                            : CHECKPOINT_INSTRUCTION).build());

            String systemPrompt = conversationSystemPrompt != null && !conversationSystemPrompt.isBlank()
                    ? conversationSystemPrompt : FALLBACK_SYSTEM_PROMPT;
            String summary = callLlm(systemPrompt, msgs);
            if (summary == null || summary.isBlank()) {
                return null;
            }

            // 收缩保证：摘要必须严格小于被压缩区，否则截断到预算内
            summary = enforceShrink(summary, sourceTokens, summaryMaxTokens);
            return summary;
        } catch (Exception e) {
            log.warn("检查点摘要生成失败（调用方降级为修剪）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 估算被压缩区的 token 成本（含旧摘要）
     */
    private int estimateSourceTokens(List<MemoryManager.Memory> messages, String previousSummary) {
        int total = TokenEstimator.estimateTokens(previousSummary);
        for (MemoryManager.Memory m : messages) {
            total += TokenEstimator.estimateTokens(m.content());
        }
        return total;
    }

    /**
     * 收缩硬保证：摘要估算 token 必须小于被压缩区 token。
     * 超出时截断到 summaryMaxTokens（CJK 1 字/token 的保守上界），仍不满足则放弃。
     */
    private String enforceShrink(String summary, int sourceTokens, int summaryMaxTokens) {
        if (TokenEstimator.estimateTokens(summary) < sourceTokens) {
            return summary;
        }
        String truncated = summary.substring(0, Math.min(summary.length(), summaryMaxTokens))
                + "\n\n[...摘要已截断...]";
        if (TokenEstimator.estimateTokens(truncated) < sourceTokens) {
            log.info("检查点摘要超出被压缩区，已截断到 {} 字符", summaryMaxTokens);
            return truncated;
        }
        log.warn("检查点摘要无法满足收缩保证（区域 {} tokens），放弃摘要", sourceTokens);
        return null;
    }

    /**
     * 调用 LLM 生成摘要（构建临时 ReActAgent，空工具集）
     *
     * @param systemPrompt 系统提示词（检查点路径传会话自己的提示词以命中前缀缓存）
     * @param msgs         完整消息列表（含最后的压缩指令）
     */
    private String callLlm(String systemPrompt, List<Msg> msgs) {
        try {
            AgentConfig.LLMConfig llmConfig = new AgentConfig.LLMConfig();
            // 摘要输出限长（此前配置项未生效，这里补上）
            llmConfig.setMaxTokens(agentProperties.getMemory().getCompression().getSummaryMaxTokens());
            String apiKey = resolveApiKey();
            Model model = chatModelFactory.create(llmConfig, agentProperties.getLlm(), apiKey);

            ReActAgent agent = ReActAgent.builder()
                    .name("ContextSummarizer")
                    .sysPrompt(systemPrompt)
                    .model(model)
                    .toolkit(new Toolkit())
                    .build();

            Msg response = agent.call(msgs).block();

            return response != null ? response.getTextContent() : null;
        } catch (Exception e) {
            log.warn("摘要 LLM 调用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 API Key（优先全局配置，回退环境变量）
     */
    private String resolveApiKey() {
        String apiKey = agentProperties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("LLM_API_KEY");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("DASHSCOPE_API_KEY");
        }
        return apiKey;
    }
}
