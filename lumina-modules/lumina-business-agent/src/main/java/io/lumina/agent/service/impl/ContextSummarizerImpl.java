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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 上下文压缩服务实现
 *
 * <p>调用 LLM 对旧对话消息生成摘要，替代直接丢弃。
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

    private static final String SUMMARIZER_PROMPT = """
            你是对话摘要助手。将给定的对话历史压缩成简洁的摘要。

            规则：
            - 保留关键信息：用户意图、已做决策、重要数据、未解决的问题
            - 丢弃无关内容：寒暄、重复确认、情绪表达
            - 用第三人称客观描述，不要编造未提及的信息
            - 输出纯文本摘要，不要 JSON 或 Markdown 格式
            """;

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

            return callLlm(prompt);
        } catch (Exception e) {
            log.warn("上下文摘要生成失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 调用 LLM 生成摘要（构建临时 ReActAgent，空工具集）
     */
    private String callLlm(String prompt) {
        try {
            AgentConfig.LLMConfig llmConfig = new AgentConfig.LLMConfig();
            String apiKey = resolveApiKey();
            Model model = chatModelFactory.create(llmConfig, agentProperties.getLlm(), apiKey);

            ReActAgent agent = ReActAgent.builder()
                    .name("ContextSummarizer")
                    .sysPrompt(SUMMARIZER_PROMPT)
                    .model(model)
                    .toolkit(new Toolkit())
                    .build();

            Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(prompt).build();
            Msg response = agent.call(List.of(userMsg)).block();

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
