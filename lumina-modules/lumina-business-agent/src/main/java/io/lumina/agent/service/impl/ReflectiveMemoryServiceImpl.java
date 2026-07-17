package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.infrastructure.entity.LongTermMemoryDO;
import io.lumina.agent.infrastructure.mapper.LongTermMemoryMapper;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.agent.service.ReflectiveMemoryService;
import io.lumina.agent.util.JsonUtils;
import io.lumina.common.core.BaseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 反思记忆服务实现
 *
 * <p>对话后异步调用 LLM 提取关键事实，存入 lumina_long_term_memory 表。
 * 简单文本去重（content 完全相同的跳过）。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReflectiveMemoryServiceImpl implements ReflectiveMemoryService {

    private final LongTermMemoryMapper memoryMapper;
    private final ChatModelFactory chatModelFactory;
    private final LuminaAgentProperties agentProperties;

    @Autowired(required = false)
    private io.lumina.agent.engine.AgentExecutionEngine agentExecutionEngine;

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private static final String EXTRACTION_PROMPT = """
            你是一个记忆提取助手。从以下对话中提取值得长期记忆的关键事实。

            规则：
            - 只提取有价值的信息（用户偏好、身份、项目信息、重要决策、技术栈等）
            - 闲聊/问候/情绪表达/临时问题不提取
            - 每条事实用简洁的陈述句，不超过 50 字
            - 最多提取 %d 条
            - 如果没有值得记忆的内容，返回 {"facts": []}

            输出格式（纯 JSON，不要 markdown）：
            {"facts": ["事实1", "事实2"]}

            对话：
            用户: %s
            助手: %s
            """;

    @Override
    public void extractAndSave(Long userId, Long agentId, String conversationId,
                                String userMessage, String assistantReply) {
        LuminaAgentProperties.ReflectiveConfig config = agentProperties.getMemory().getReflective();
        if (!config.isEnabled()) {
            return;
        }

        if (userId == null || userMessage == null || assistantReply == null) {
            return;
        }

        // 截断过长的对话内容（控制 Token 消耗）
        String truncatedUser = truncate(userMessage, 2000);
        String truncatedReply = truncate(assistantReply, 2000);

        try {
            // 调用 LLM 提取事实
            String prompt = String.format(EXTRACTION_PROMPT,
                    config.getMaxFactsPerTurn(), truncatedUser, truncatedReply);
            String llmResponse = callLlm(prompt);

            if (llmResponse == null || llmResponse.isBlank()) {
                return;
            }

            // 解析 JSON 提取事实
            List<String> facts = parseFacts(llmResponse, config.getMaxFactLength());
            if (facts.isEmpty()) {
                log.debug("反思记忆提取: 无值得记忆的内容, conversation={}", conversationId);
                return;
            }

            // 去重：查出现有记忆内容
            Set<String> existingContents = getExistingContents(userId, agentId);

            Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
            int saved = 0;
            for (String fact : facts) {
                if (existingContents.contains(fact)) {
                    continue; // 简单文本去重
                }
                LongTermMemoryDO memory = new LongTermMemoryDO();
                memory.setUserId(userId);
                memory.setAgentId(agentId);
                memory.setConversationId(conversationId);
                memory.setMemoryType("fact");
                memory.setContent(fact);
                memory.setImportance(new BigDecimal("0.50"));
                memory.setAccessCount(0);
                memory.setTenantId(tenantId);
                memoryMapper.insert(memory);
                saved++;
            }

            if (saved > 0) {
                log.info("反思记忆提取成功: conversation={}, 新增 {} 条事实", conversationId, saved);
            }

        } catch (Exception e) {
            log.warn("反思记忆提取失败（不影响主流程）: conversation={}, error={}", conversationId, e.getMessage());
        }
    }

    @Override
    public List<String> getLongTermMemories(Long userId, Long agentId) {
        if (userId == null) {
            return List.of();
        }

        int max = agentProperties.getMemory().getReflective().getMaxContextMemories();

        LambdaQueryWrapper<LongTermMemoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LongTermMemoryDO::getUserId, userId);
        if (agentId != null) {
            wrapper.and(w -> w.eq(LongTermMemoryDO::getAgentId, agentId)
                    .or().isNull(LongTermMemoryDO::getAgentId));
        }
        wrapper.orderByDesc(LongTermMemoryDO::getImportance)
                .orderByDesc(LongTermMemoryDO::getCreateTime)
                .last("LIMIT " + max);

        return memoryMapper.selectList(wrapper).stream()
                .map(LongTermMemoryDO::getContent)
                .collect(Collectors.toList());
    }

    // ==================== 内部方法 ====================

    /**
     * 调用 LLM（构建临时 ReActAgent，空工具集）
     */
    private String callLlm(String prompt) {
        try {
            AgentConfig.LLMConfig llmConfig = new AgentConfig.LLMConfig();
            String apiKey = resolveApiKey();
            Model model = chatModelFactory.create(llmConfig, agentProperties.getLlm(), apiKey);

            ReActAgent agent = ReActAgent.builder()
                    .name("MemoryExtractor")
                    .sysPrompt("你是一个 JSON 提取助手。只输出 JSON，不输出其他内容。")
                    .model(model)
                    .toolkit(new Toolkit())  // 空工具集
                    .memory(new InMemoryMemory())
                    .build();

            Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(prompt).build();
            Msg response = agent.call(List.of(userMsg)).block();

            return response != null ? response.getTextContent() : null;
        } catch (Exception e) {
            log.warn("反思记忆 LLM 调用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 LLM 返回的 JSON 提取事实列表
     */
    private List<String> parseFacts(String llmResponse, int maxLength) {
        List<String> facts = new ArrayList<>();

        // 提取 JSON 对象部分（LLM 可能输出额外文字）
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(llmResponse);
        if (!matcher.find()) {
            return facts;
        }

        try {
            JsonNode root = JsonUtils.OBJECT_MAPPER.readTree(matcher.group());
            JsonNode factsNode = root.path("facts");
            if (!factsNode.isArray()) {
                return facts;
            }
            for (JsonNode fact : factsNode) {
                String text = fact.asText("").trim();
                if (!text.isBlank() && text.length() <= maxLength) {
                    facts.add(text);
                }
            }
        } catch (Exception e) {
            log.debug("反思记忆 JSON 解析失败: {}", e.getMessage());
        }

        return facts;
    }

    /**
     * 查询现有记忆内容集合（用于去重）
     */
    private Set<String> getExistingContents(Long userId, Long agentId) {
        LambdaQueryWrapper<LongTermMemoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LongTermMemoryDO::getUserId, userId);
        if (agentId != null) {
            wrapper.eq(LongTermMemoryDO::getAgentId, agentId);
        }
        wrapper.select(LongTermMemoryDO::getContent);

        return memoryMapper.selectList(wrapper).stream()
                .map(LongTermMemoryDO::getContent)
                .collect(Collectors.toSet());
    }

    private String resolveApiKey() {
        String apiKey = agentProperties.getLlm().getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        apiKey = System.getenv("LLM_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            log.warn("反思记忆使用已废弃的 DASHSCOPE_API_KEY，请改用 LLM_API_KEY");
            return apiKey;
        }
        throw new IllegalStateException("LLM API Key 未配置，无法提取反思记忆");
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
