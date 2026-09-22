package io.lumina.agent.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.infrastructure.entity.ConversationDO;
import io.lumina.agent.infrastructure.entity.LongTermMemoryDO;
import io.lumina.agent.infrastructure.entity.MessageDO;
import io.lumina.agent.infrastructure.mapper.ConversationMapper;
import io.lumina.agent.infrastructure.mapper.LongTermMemoryMapper;
import io.lumina.agent.infrastructure.mapper.MessageMapper;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.agent.util.JsonUtils;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.LoginContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 记忆整理代理（v3.14 批次 4.1，借鉴 ZCode 记忆整理）
 *
 * <p>后台作业对<b>已归档的长会话</b>（消息数达标 + 闲置超阈值）做 LLM 提炼，
 * 产出带召回打分（importance）的长期记忆（{@code memory_type = compaction}），
 * 经既有长期记忆注入通道（buildContextMessages）参与后续会话。
 *
 * <p>费用与安全三闸门（路线图硬约束）：
 * <ul>
 *   <li><b>默认关闭</b>：{@code lumina.agent.memory.compaction.enabled=false}（本作业
 *       由 @ConditionalOnProperty 装配，关闭时 Bean 都不存在）</li>
 *   <li><b>租户白名单</b>：开启后仍只整理列入 {@code tenant-whitelist} 的租户
 *      （空名单 = 无租户生效——租户级费用授权必须显式列举）</li>
 *   <li><b>计量硬上限</b>：每轮最多 {@code max-conversations-per-run} 个会话
 *       （每会话恰好一次 LLM 调用），全部计量入指标与日志</li>
 * </ul>
 *
 * <p>租户上下文：@Scheduled 线程无 BaseContext——按会话归属租户构造 system
 * LoginContext（与 AgentTriggerServiceImpl.fireInternal 同模式），单会话粒度切换。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lumina.agent.memory.compaction", name = "enabled", havingValue = "true")
public class MemoryCompactionJob {

    /** 整理记忆类型标记（区别于反思提取的 per-turn 事实） */
    static final String MEMORY_TYPE_COMPACTION = "compaction";

    private static final String DISTILL_PROMPT = """
            你是记忆整理助手。以下是一个已归档的会话记录。请提炼跨会话仍有长期价值的记忆。

            规则：
            - 只提炼有长期价值的信息（用户目标/偏好、项目事实、重要决策、约束与承诺等）
            - 只看这个会话本身，闲聊、过程性内容、临时问题不入库
            - 每条记忆一个简洁陈述句（不超过 50 字），并给出召回打分 importance（0-1，越高越值得在后续会话注入）
            - 最多提炼 %d 条；没有值得保留的内容返回 {"memories": []}

            输出格式（纯 JSON，不要 markdown）：
            {"memories": [{"content": "...", "importance": 0.8}]}

            会话标题: %s
            会话记录:
            %s
            """;

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final LongTermMemoryMapper memoryMapper;
    private final ChatModelFactory chatModelFactory;
    private final LuminaAgentProperties agentProperties;

    /**
     * 定时整理：按租户白名单逐租户扫描候选会话，提炼 + 打分 + 入库。
     * 单会话失败不影响其余会话；整轮异常只告警（后台作业绝不影响前台）。
     */
    @Scheduled(fixedDelayString = "${lumina.agent.memory.compaction.interval-ms:3600000}",
            initialDelayString = "${lumina.agent.memory.compaction.initial-delay-ms:300000}")
    public void compact() {
        LuminaAgentProperties.MemoryCompactionConfig config = agentProperties.getMemory().getCompaction();
        Set<Long> tenants = new TreeSet<>(config.getTenantWhitelist() != null
                ? config.getTenantWhitelist() : Set.of());
        if (tenants.isEmpty()) {
            log.debug("记忆整理代理：租户白名单为空，本轮跳过");
            return;
        }
        int totalConversations = 0;
        int totalMemories = 0;
        long start = System.currentTimeMillis();
        for (Long tenantId : tenants) {
            try {
                int[] counts = compactTenant(tenantId, config);
                totalConversations += counts[0];
                totalMemories += counts[1];
            } catch (Exception e) {
                log.error("记忆整理代理：租户 {} 整理异常", tenantId, e);
            }
        }
        log.info("记忆整理代理本轮完成: 租户数={}, 整理会话={}, 产出记忆={}, 耗时 {}ms（计量：会话数即 LLM 调用数）",
                tenants.size(), totalConversations, totalMemories, System.currentTimeMillis() - start);
    }

    /** 单租户整理，返回 [整理会话数, 产出记忆数] */
    private int[] compactTenant(Long tenantId, LuminaAgentProperties.MemoryCompactionConfig config) {
        LoginContext systemContext = new LoginContext(
                tenantId, 0L, "system", new String[]{"SYSTEM"}, null);
        BaseContext.setCurrent(systemContext);
        try {
            List<ConversationDO> candidates = findCandidates(config);
            if (candidates.isEmpty()) {
                return new int[]{0, 0};
            }
            Set<String> alreadyCompacted = findCompactedConversationUuids(candidates);
            int conversations = 0;
            int memories = 0;
            for (ConversationDO conversation : candidates) {
                if (alreadyCompacted.contains(conversation.getConversationUuid())) {
                    continue;
                }
                try {
                    memories += compactConversation(conversation, config);
                    conversations++;
                } catch (Exception e) {
                    log.warn("记忆整理代理：会话 {} 整理失败: {}",
                            conversation.getConversationUuid(), e.getMessage());
                }
                if (conversations >= config.getMaxConversationsPerRun()) {
                    break;
                }
            }
            return new int[]{conversations, memories};
        } finally {
            BaseContext.clear();
        }
    }

    /** 候选会话：消息数达标 + 闲置超阈值（租户条件由 TenantLineHandler 自动附加） */
    private List<ConversationDO> findCandidates(LuminaAgentProperties.MemoryCompactionConfig config) {
        LocalDateTime idleBefore = LocalDateTime.now().minusDays(Math.max(0, config.getIdleDays()));
        return conversationMapper.selectList(new LambdaQueryWrapper<ConversationDO>()
                .ge(ConversationDO::getMessageCount, config.getMinMessages())
                .lt(ConversationDO::getUpdateTime, idleBefore)
                .orderByAsc(ConversationDO::getUpdateTime)
                .last("LIMIT " + (config.getMaxConversationsPerRun() * 3)));
    }

    /** 已整理过的会话 UUID（幂等：同一会话只整理一次） */
    private Set<String> findCompactedConversationUuids(List<ConversationDO> candidates) {
        Set<String> existingUuids = new HashSet<>();
        List<LongTermMemoryDO> existing = memoryMapper.selectList(new LambdaQueryWrapper<LongTermMemoryDO>()
                .eq(LongTermMemoryDO::getMemoryType, MEMORY_TYPE_COMPACTION)
                .select(LongTermMemoryDO::getConversationId));
        for (LongTermMemoryDO m : existing) {
            if (m.getConversationId() != null) {
                existingUuids.add(m.getConversationId());
            }
        }
        Set<String> compacted = new HashSet<>();
        for (ConversationDO c : candidates) {
            if (existingUuids.contains(c.getConversationUuid())) {
                compacted.add(c.getConversationUuid());
            }
        }
        return compacted;
    }

    /** 单会话提炼入库，返回产出记忆条数 */
    private int compactConversation(ConversationDO conversation,
                                    LuminaAgentProperties.MemoryCompactionConfig config) {
        String transcript = buildTranscript(conversation.getConversationId(), config.getMaxPromptChars());
        if (transcript.isBlank()) {
            return 0;
        }
        String prompt = String.format(DISTILL_PROMPT, config.getMaxFactsPerConversation(),
                conversation.getTitle() != null ? conversation.getTitle() : "(无标题)", transcript);
        String llmResponse = callLlm(prompt);
        List<Map<String, Object>> memories = parseMemories(llmResponse, config.getMaxFactsPerConversation());
        if (memories.isEmpty()) {
            log.debug("记忆整理代理：会话 {} 无值得保留的内容", conversation.getConversationUuid());
            return 0;
        }
        int saved = 0;
        for (Map<String, Object> memory : memories) {
            String content = String.valueOf(memory.get("content")).trim();
            if (content.isEmpty() || "null".equals(content)) {
                continue;
            }
            LongTermMemoryDO row = new LongTermMemoryDO();
            row.setUserId(conversation.getUserId());
            row.setAgentId(conversation.getAgentId());
            row.setConversationId(conversation.getConversationUuid());
            row.setMemoryType(MEMORY_TYPE_COMPACTION);
            row.setContent(content);
            row.setImportance(parseImportance(memory.get("importance")));
            row.setAccessCount(0);
            row.setTenantId(conversation.getTenantId());
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(LocalDateTime.now());
            memoryMapper.insert(row);
            saved++;
        }
        log.info("记忆整理代理: conversation={}, agentId={}, 产出 {} 条记忆（每会话一次 LLM 调用）",
                conversation.getConversationUuid(), conversation.getAgentId(), saved);
        return saved;
    }

    /** 会话摘录：从最新向最旧装填到字符上限（与上下文预算同"宁可少带"纪律） */
    String buildTranscript(Long conversationId, int maxChars) {
        List<MessageDO> messages = messageMapper.selectList(new LambdaQueryWrapper<MessageDO>()
                .eq(MessageDO::getConversationId, conversationId)
                .orderByDesc(MessageDO::getMessageId)
                .last("LIMIT 200"));
        if (messages.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        int used = 0;
        for (int i = messages.size() - 1; i >= 0; i--) { // 时间正序呈现
            MessageDO message = messages.get(i);
            String line = ("assistant".equals(message.getRole()) ? "助手" : "用户") + ": "
                    + truncate(message.getContent(), 800);
            if (used + line.length() > maxChars) {
                lines.add(0, "[...更早的 " + i + " 条消息已省略...]");
                break;
            }
            lines.add(0, line);
            used += line.length();
        }
        return String.join("\n", lines);
    }

    private String callLlm(String prompt) {
        try {
            AgentConfig.LLMConfig llmConfig = new AgentConfig.LLMConfig();
            String apiKey = resolveApiKey();
            Model model = chatModelFactory.create(llmConfig, agentProperties.getLlm(), apiKey);
            ReActAgent agent = ReActAgent.builder()
                    .name("MemoryCompactor")
                    .sysPrompt("你是一个记忆整理助手。只输出 JSON，不输出其他内容。")
                    .model(model)
                    .toolkit(new Toolkit())
                    .build();
            Msg response = agent.call(List.of(
                            Msg.builder().role(MsgRole.USER).textContent(prompt).build()))
                    .block();
            return response != null ? response.getTextContent() : null;
        } catch (Exception e) {
            log.warn("记忆整理代理 LLM 调用失败: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> parseMemories(String llmResponse, int max) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (llmResponse == null || llmResponse.isBlank()) {
            return result;
        }
        try {
            int start = llmResponse.indexOf('{');
            int end = llmResponse.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return result;
            }
            Map<String, Object> parsed = JsonUtils.OBJECT_MAPPER.readValue(
                    llmResponse.substring(start, end + 1), Map.class);
            Object memories = parsed.get("memories");
            if (!(memories instanceof List<?> list)) {
                return result;
            }
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && map.get("content") != null) {
                    result.add((Map<String, Object>) map);
                    if (result.size() >= max) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("记忆整理代理：LLM 输出解析失败: {}", e.getMessage());
        }
        return result;
    }

    BigDecimal parseImportance(Object value) {
        try {
            double importance = value != null ? Double.parseDouble(String.valueOf(value)) : 0.5;
            return BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, importance)));
        } catch (NumberFormatException e) {
            return BigDecimal.valueOf(0.5);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private String resolveApiKey() {
        String apiKey = agentProperties.getLlm().getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        apiKey = System.getenv("LLM_API_KEY");
        return apiKey != null && !apiKey.isBlank() ? apiKey : System.getenv("DASHSCOPE_API_KEY");
    }
}
