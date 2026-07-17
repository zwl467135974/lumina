package io.lumina.agent.service;

import io.lumina.agent.infrastructure.entity.LongTermMemoryDO;
import io.lumina.agent.infrastructure.mapper.LongTermMemoryMapper;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.LoginContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflective Memory 集成测试
 *
 * <p>验证 V33 迁移（lumina_long_term_memory 表存在）+ Mapper CRUD + getLongTermMemories 查询。
 * 不验证 LLM 调用（需要真实 API Key + 网络），仅验证 DB 层。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ReflectiveMemoryIntegrationTest {

    @Autowired
    private LongTermMemoryMapper memoryMapper;

    private static final Long TEST_USER_ID = 99999L;
    private static final Long TEST_AGENT_ID = 88888L;
    private static final Long TEST_TENANT_ID = 0L;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrent(new LoginContext(TEST_TENANT_ID, TEST_USER_ID, "test", null, null));
        // 清理可能的残留测试数据
        cleanupTestData();
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
        BaseContext.clear();
    }

    @Test
    void tableExistsAndInsertWorks() {
        // 验证 V33 迁移创建了表 + insert 可用
        LongTermMemoryDO memory = new LongTermMemoryDO();
        memory.setUserId(TEST_USER_ID);
        memory.setAgentId(TEST_AGENT_ID);
        memory.setConversationId("test-conv-uuid");
        memory.setMemoryType("fact");
        memory.setContent("测试事实：用户是 Java 开发者");
        memory.setImportance(new BigDecimal("0.80"));
        memory.setAccessCount(0);
        memory.setTenantId(TEST_TENANT_ID);

        int result = memoryMapper.insert(memory);

        assertThat(result).isEqualTo(1);
        assertThat(memory.getId()).isNotNull();
    }

    @Test
    void queryByUserIdAndAgentIdReturnsMemories() {
        // 插入 3 条记忆
        insertTestMemory("事实1：用户偏好简洁回答", "0.90");
        insertTestMemory("事实2：用户使用 Spring Boot", "0.70");
        insertTestMemory("事实3：用户母语是中文", "0.85");

        // 查询（模拟 getLongTermMemories 的查询逻辑）
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LongTermMemoryDO>()
                .eq(LongTermMemoryDO::getUserId, TEST_USER_ID)
                .eq(LongTermMemoryDO::getAgentId, TEST_AGENT_ID)
                .orderByDesc(LongTermMemoryDO::getImportance)
                .orderByDesc(LongTermMemoryDO::getCreateTime);

        List<LongTermMemoryDO> memories = memoryMapper.selectList(wrapper);

        assertThat(memories).hasSize(3);
        // 按 importance DESC 排序，0.90 应在第一位
        assertThat(memories.get(0).getContent()).contains("偏好简洁");
        assertThat(memories.get(0).getImportance()).isEqualByComparingTo(new BigDecimal("0.90"));
    }

    @Test
    void deduplicationQueryWorks() {
        // 插入已有记忆
        insertTestMemory("用户是 Java 开发者", "0.50");

        // 模拟 getExistingContents 查询
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LongTermMemoryDO>()
                .eq(LongTermMemoryDO::getUserId, TEST_USER_ID)
                .eq(LongTermMemoryDO::getAgentId, TEST_AGENT_ID)
                .select(LongTermMemoryDO::getContent);

        List<LongTermMemoryDO> existing = memoryMapper.selectList(wrapper);
        var contents = existing.stream().map(LongTermMemoryDO::getContent).collect(java.util.stream.Collectors.toSet());

        assertThat(contents).contains("用户是 Java 开发者");
    }

    @Test
    void importanceOrderingForContextInjection() {
        // 模拟 getLongTermMemories 的查询：按 importance DESC + createTime DESC LIMIT 20
        for (int i = 0; i < 25; i++) {
            insertTestMemory("事实" + i, String.format("0.%02d", 99 - i));
        }

        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LongTermMemoryDO>()
                .eq(LongTermMemoryDO::getUserId, TEST_USER_ID)
                .eq(LongTermMemoryDO::getAgentId, TEST_AGENT_ID)
                .orderByDesc(LongTermMemoryDO::getImportance)
                .orderByDesc(LongTermMemoryDO::getCreateTime)
                .last("LIMIT 20");

        List<LongTermMemoryDO> memories = memoryMapper.selectList(wrapper);

        assertThat(memories).hasSize(20); // LIMIT 20 生效
        // 第一条应该是 importance 最高的（0.99）
        assertThat(memories.get(0).getImportance()).isEqualByComparingTo(new BigDecimal("0.99"));
    }

    // ==================== 辅助方法 ====================

    private void insertTestMemory(String content, String importance) {
        LongTermMemoryDO memory = new LongTermMemoryDO();
        memory.setUserId(TEST_USER_ID);
        memory.setAgentId(TEST_AGENT_ID);
        memory.setConversationId("test-conv");
        memory.setMemoryType("fact");
        memory.setContent(content);
        memory.setImportance(new BigDecimal(importance));
        memory.setAccessCount(0);
        memory.setTenantId(TEST_TENANT_ID);
        memoryMapper.insert(memory);
    }

    private void cleanupTestData() {
        try {
            memoryMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LongTermMemoryDO>()
                    .eq(LongTermMemoryDO::getUserId, TEST_USER_ID));
        } catch (Exception ignored) {
        }
    }
}
