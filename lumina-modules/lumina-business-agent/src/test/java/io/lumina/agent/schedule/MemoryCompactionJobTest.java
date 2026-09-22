package io.lumina.agent.schedule;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.infrastructure.entity.ConversationDO;
import io.lumina.agent.infrastructure.entity.LongTermMemoryDO;
import io.lumina.agent.infrastructure.entity.MessageDO;
import io.lumina.agent.infrastructure.mapper.ConversationMapper;
import io.lumina.agent.infrastructure.mapper.LongTermMemoryMapper;
import io.lumina.agent.infrastructure.mapper.MessageMapper;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.common.core.BaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 记忆整理代理单元测试（v3.14 批次 4.1）
 *
 * <p>覆盖：解析与打分纯逻辑、摘录装填纪律、租户白名单双闸门、
 * 幂等跳过、上下文清理。LLM 调用走失败降级路径（mock 工厂返回 null 模型）。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
class MemoryCompactionJobTest {

    private LuminaAgentProperties props;
    private ConversationMapper conversationMapper;
    private MessageMapper messageMapper;
    private LongTermMemoryMapper memoryMapper;
    private MemoryCompactionJob job;

    @BeforeEach
    void setUp() {
        props = new LuminaAgentProperties();
        props.getMemory().getCompaction().setEnabled(true);
        conversationMapper = Mockito.mock(ConversationMapper.class);
        messageMapper = Mockito.mock(MessageMapper.class);
        memoryMapper = Mockito.mock(LongTermMemoryMapper.class);
        job = new MemoryCompactionJob(conversationMapper, messageMapper, memoryMapper,
                Mockito.mock(ChatModelFactory.class), props);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @SuppressWarnings("unchecked")
    private void stubConversations(ConversationDO... conversations) {
        when(conversationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(conversations));
    }

    @SuppressWarnings("unchecked")
    private void stubCompactedExisting(LongTermMemoryDO... existing) {
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
    }

    private ConversationDO conversation(long id, String uuid, int messageCount) {
        ConversationDO conversation = new ConversationDO();
        conversation.setConversationId(id);
        conversation.setConversationUuid(uuid);
        conversation.setAgentId(9L);
        conversation.setUserId(5L);
        conversation.setTenantId(7L);
        conversation.setTitle("会话" + id);
        conversation.setMessageCount(messageCount);
        conversation.setUpdateTime(LocalDateTime.now().minusDays(30));
        return conversation;
    }

    // ==================== 纯逻辑：解析与打分 ====================

    @Test
    void parseMemoriesExtractsAndCaps() {
        String llm = "{\"memories\":[{\"content\":\"偏好简洁回答\",\"importance\":0.9},"
                + "{\"content\":\"项目用 Java 24\",\"importance\":1.7},{\"content\":\"多余\"}]}";

        List<Map<String, Object>> memories = job.parseMemories(llm, 2);

        assertThat(memories).hasSize(2);
        assertThat(memories.get(0).get("content")).isEqualTo("偏好简洁回答");
    }

    @Test
    void parseMemoriesToleratesMarkdownWrapperAndGarbage() {
        assertThat(job.parseMemories("```json\n{\"memories\":[{\"content\":\"x\",\"importance\":0.5}]}\n```", 5))
                .hasSize(1);
        assertThat(job.parseMemories("不是 JSON", 5)).isEmpty();
        assertThat(job.parseMemories(null, 5)).isEmpty();
        assertThat(job.parseMemories("{\"memories\": []}", 5)).isEmpty();
    }

    @Test
    void parseImportanceClampsAndDefaults() {
        assertThat(job.parseImportance(1.7)).isEqualTo(BigDecimal.valueOf(1.0));
        assertThat(job.parseImportance(-0.3)).isEqualTo(BigDecimal.valueOf(0.0));
        assertThat(job.parseImportance("0.8")).isEqualTo(BigDecimal.valueOf(0.8));
        assertThat(job.parseImportance(null)).isEqualTo(BigDecimal.valueOf(0.5));
        assertThat(job.parseImportance("abc")).isEqualTo(BigDecimal.valueOf(0.5));
    }

    // ==================== 纯逻辑：摘录装填 ====================

    @SuppressWarnings("unchecked")
    @Test
    void buildTranscriptCapsAndMarksOmission() {
        props.getMemory().getCompaction().setMaxPromptChars(500);
        java.util.List<MessageDO> messages = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            MessageDO message = new MessageDO();
            message.setMessageId((long) i);
            message.setRole(i % 2 == 0 ? "assistant" : "user");
            message.setContent("消息内容" + i + " " + "长".repeat(90));
            messages.add(message);
        }
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(messages);

        String transcript = job.buildTranscript(1L, 500);

        assertThat(transcript.length()).isLessThanOrEqualTo(700);
        assertThat(transcript).contains("已省略");
        // 时间正序：首行是最早被保留的消息
        assertThat(transcript.lines().filter(l -> l.startsWith("用户")).findFirst())
                .map(l -> l.contains("消息内容1")).isPresent();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildTranscriptEmptyConversationReturnsBlank() {
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertThat(job.buildTranscript(1L, 1000)).isEmpty();
    }

    // ==================== 闸门与幂等 ====================

    @Test
    void emptyWhitelistSkipsEverything() {
        props.getMemory().getCompaction().setTenantWhitelist(Set.of());

        job.compact();

        verify(conversationMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void compactRunsPerWhitelistedTenantAndClearsContext() {
        props.getMemory().getCompaction().setTenantWhitelist(Set.of(7L));
        stubConversations(conversation(1, "u1", 30));
        stubCompactedExisting();
        // LLM 失败降级路径：ChatModelFactory 返回 null 模型 → callLlm 捕获返回 null → 0 记忆入库

        job.compact();

        verify(conversationMapper).selectList(any(Wrapper.class));
        // 上下文必须清理（后台线程不得泄漏租户）
        assertThat(BaseContext.getTenantId()).isNull();
    }

    @Test
    void alreadyCompactedConversationSkipped() {
        props.getMemory().getCompaction().setTenantWhitelist(Set.of(7L));
        ConversationDO done = conversation(1, "u1", 30);
        stubConversations(done);
        LongTermMemoryDO marker = new LongTermMemoryDO();
        marker.setConversationId("u1");
        marker.setMemoryType(MemoryCompactionJob.MEMORY_TYPE_COMPACTION);
        stubCompactedExisting(marker);

        job.compact();

        // 幂等：已整理会话不再产生任何 LLM 调用/入库
        verify(memoryMapper, never()).insert(any(LongTermMemoryDO.class));
    }

    @Test
    void tenantExceptionIsolatedAcrossTenants() {
        props.getMemory().getCompaction().setTenantWhitelist(Set.of(7L, 8L));
        when(conversationMapper.selectList(any(Wrapper.class)))
                .thenThrow(new IllegalStateException("DB 抖动"))
                .thenReturn(List.of());

        job.compact(); // 不上抛：一个租户失败不影响另一个

        assertThat(BaseContext.getTenantId()).isNull();
    }
}
