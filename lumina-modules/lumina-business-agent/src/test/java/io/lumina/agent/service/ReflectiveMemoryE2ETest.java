package io.lumina.agent.service;

import io.lumina.agent.infrastructure.entity.LongTermMemoryDO;
import io.lumina.agent.infrastructure.mapper.LongTermMemoryMapper;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.LoginContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflective Memory 端到端测试
 *
 * <p>使用真实 LLM API（需要 LLM_API_KEY 环境变量）验证完整链路：
 * 对话 → LLM 提取事实 → JSON 解析 → 去重 → DB 持久化。
 *
 * <p>跳过条件：LLM_API_KEY 未设置时自动跳过。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "lumina.agent.memory.reflective.enabled=true",
    "lumina.agent.llm.type=glm",
    "lumina.agent.llm.model=glm-4-flash",
    "lumina.agent.llm.api-key=${LLM_API_KEY:}"
})
class ReflectiveMemoryE2ETest {

    @Autowired
    private ReflectiveMemoryService reflectiveMemoryService;

    @Autowired
    private LongTermMemoryMapper memoryMapper;

    private static final Long TEST_USER_ID = 77777L;
    private static final Long TEST_AGENT_ID = 66666L;
    private static final Long TEST_TENANT_ID = 0L;

    private static boolean hasApiKey() {
        String key = System.getenv("LLM_API_KEY");
        return key != null && !key.isBlank();
    }

    @BeforeEach
    void setUp() {
        BaseContext.setCurrent(new LoginContext(TEST_TENANT_ID, TEST_USER_ID, "test", null, null));
        cleanupTestData();
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
        BaseContext.clear();
    }

    @Test
    void realLlmExtractsFactsFromConversation() {
        if (!hasApiKey()) {
            System.out.println("⚠ LLM_API_KEY 未设置，跳过真实 LLM 端到端测试");
            Assumptions.assumeTrue(false, "LLM_API_KEY not set");
            return;
        }

        // 模拟一段有价值的对话（包含用户身份/偏好信息）
        String userMessage = "我是做 Java 后端开发的，主要技术栈是 Spring Boot 和 MyBatis。"
                + "你能帮我用中文回答吗？我不喜欢太啰嗦的回答。";
        String assistantReply = "好的，了解了。你是 Java 后端开发者，使用 Spring Boot 和 MyBatis。"
                + "后续我会用中文简洁地回答你的问题。有什么可以帮你的？";

        // 执行提取
        reflectiveMemoryService.extractAndSave(TEST_USER_ID, TEST_AGENT_ID, "e2e-conv-1",
                userMessage, assistantReply);

        // 验证 DB 中有记忆写入
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LongTermMemoryDO>()
                .eq(LongTermMemoryDO::getUserId, TEST_USER_ID)
                .eq(LongTermMemoryDO::getAgentId, TEST_AGENT_ID);
        List<LongTermMemoryDO> memories = memoryMapper.selectList(wrapper);

        System.out.println("=== 端到端测试结果 ===");
        System.out.println("提取到的记忆条数: " + memories.size());
        for (LongTermMemoryDO m : memories) {
            System.out.println("  - [" + m.getMemoryType() + "] " + m.getContent()
                    + " (importance=" + m.getImportance() + ")");
        }

        // LLM 至少应该提取出 1 条事实
        assertThat(memories).isNotEmpty();

        // 验证提取的内容包含关键信息（Java/后端/Spring Boot/中文 之一）
        boolean hasRelevantFact = memories.stream()
                .anyMatch(m -> {
                    String c = m.getContent().toLowerCase();
                    return c.contains("java") || c.contains("后端") || c.contains("spring")
                            || c.contains("中文") || c.contains("backend");
                });
        assertThat(hasRelevantFact)
                .as("提取的记忆应包含 Java/后端/Spring Boot/中文 等关键信息")
                .isTrue();
    }

    @Test
    void realLlmSkipsGossipConversation() {
        if (!hasApiKey()) {
            Assumptions.assumeTrue(false, "LLM_API_KEY not set");
            return;
        }

        // 模拟一段无价值的闲聊
        String userMessage = "你好啊";
        String assistantReply = "你好！有什么可以帮你的吗？";

        reflectiveMemoryService.extractAndSave(TEST_USER_ID, TEST_AGENT_ID, "e2e-conv-2",
                userMessage, assistantReply);

        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LongTermMemoryDO>()
                .eq(LongTermMemoryDO::getUserId, TEST_USER_ID)
                .eq(LongTermMemoryDO::getAgentId, TEST_AGENT_ID)
                .eq(LongTermMemoryDO::getConversationId, "e2e-conv-2");
        List<LongTermMemoryDO> memories = memoryMapper.selectList(wrapper);

        System.out.println("=== 闲聊测试结果 ===");
        System.out.println("闲聊提取到的记忆条数: " + memories.size() + "（期望 0）");

        // 闲聊不应提取出任何记忆
        assertThat(memories).isEmpty();
    }

    @Test
    void realLlmDeduplicatesRepeatedFacts() {
        if (!hasApiKey()) {
            Assumptions.assumeTrue(false, "LLM_API_KEY not set");
            return;
        }

        String userMessage = "我是 Python 开发者";
        String assistantReply = "了解，你是 Python 开发者。";

        // 同一对话调用两次
        reflectiveMemoryService.extractAndSave(TEST_USER_ID, TEST_AGENT_ID, "e2e-conv-3",
                userMessage, assistantReply);
        reflectiveMemoryService.extractAndSave(TEST_USER_ID, TEST_AGENT_ID, "e2e-conv-3",
                userMessage, assistantReply);

        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LongTermMemoryDO>()
                .eq(LongTermMemoryDO::getUserId, TEST_USER_ID)
                .eq(LongTermMemoryDO::getAgentId, TEST_AGENT_ID)
                .eq(LongTermMemoryDO::getConversationId, "e2e-conv-3");
        List<LongTermMemoryDO> memories = memoryMapper.selectList(wrapper);

        System.out.println("=== 去重测试结果 ===");
        System.out.println("重复对话后的记忆条数: " + memories.size() + "（去重后应远少于 2x）");
        for (LongTermMemoryDO m : memories) {
            System.out.println("  - " + m.getContent());
        }

        // 相同内容不应被重复存储（简单文本去重）
        long distinctContents = memories.stream()
                .map(LongTermMemoryDO::getContent)
                .distinct()
                .count();
        assertThat(distinctContents).isEqualTo(memories.size());
    }

    private void cleanupTestData() {
        try {
            memoryMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LongTermMemoryDO>()
                    .eq(LongTermMemoryDO::getUserId, TEST_USER_ID));
        } catch (Exception ignored) {
        }
    }
}
