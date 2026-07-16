package io.lumina.agent.engine;

import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PlanExecuteAgent 单元测试
 *
 * <p>重点验证 JSON 子任务解析逻辑（最易出错的纯逻辑部分）。
 * Plan/Execute/Summarize 三阶段编排依赖真实 LLM 调用，不做端到端 mock。
 *
 * @author Lumina Team
 * @since 3.3.0
 */
class PlanExecuteAgentTest {

    @Test
    void parseSubTasksValidJsonArray() throws Exception {
        PlanExecuteAgent agent = createAgent();
        Method method = PlanExecuteAgent.class.getDeclaredMethod("parseSubTasks", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> tasks = (List<String>) method.invoke(agent,
                "[{\"step\": \"搜索新闻\"}, {\"step\": \"总结要点\"}]");

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0)).isEqualTo("搜索新闻");
        assertThat(tasks.get(1)).isEqualTo("总结要点");
    }

    @Test
    void parseSubTasksWithSurroundingText() throws Exception {
        PlanExecuteAgent agent = createAgent();
        Method method = PlanExecuteAgent.class.getDeclaredMethod("parseSubTasks", String.class);
        method.setAccessible(true);

        // LLM 可能输出解释文字 + JSON 数组
        @SuppressWarnings("unchecked")
        List<String> tasks = (List<String>) method.invoke(agent,
                "Here is the plan:\n[{\"step\": \"step 1\"}, {\"step\": \"step 2\"}]\nDone.");

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0)).isEqualTo("step 1");
    }

    @Test
    void parseSubTasksEmptyArray() throws Exception {
        PlanExecuteAgent agent = createAgent();
        Method method = PlanExecuteAgent.class.getDeclaredMethod("parseSubTasks", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> tasks = (List<String>) method.invoke(agent, "[]");

        assertThat(tasks).isEmpty();
    }

    @Test
    void parseSubTasksNoJsonArray() throws Exception {
        PlanExecuteAgent agent = createAgent();
        Method method = PlanExecuteAgent.class.getDeclaredMethod("parseSubTasks", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> tasks = (List<String>) method.invoke(agent, "This is just text, no JSON");

        assertThat(tasks).isEmpty();
    }

    @Test
    void parseSubTasksMalformedJson() throws Exception {
        PlanExecuteAgent agent = createAgent();
        Method method = PlanExecuteAgent.class.getDeclaredMethod("parseSubTasks", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> tasks = (List<String>) method.invoke(agent, "[{broken json}]");

        assertThat(tasks).isEmpty();
    }

    @Test
    void parseSubTasksCapsAtMaxSubTasks() throws Exception {
        PlanExecuteAgent agent = createAgent();
        Method method = PlanExecuteAgent.class.getDeclaredMethod("parseSubTasks", String.class);
        method.setAccessible(true);

        // 生成 15 个子任务（超过 MAX_SUBTASKS=10）
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 15; i++) {
            if (i > 0) json.append(",");
            json.append("{\"step\": \"task ").append(i).append("\"}");
        }
        json.append("]");

        @SuppressWarnings("unchecked")
        List<String> tasks = (List<String>) method.invoke(agent, json.toString());

        assertThat(tasks).hasSize(10);
    }

    @Test
    void parseSubTasksMissingStepKey() throws Exception {
        PlanExecuteAgent agent = createAgent();
        Method method = PlanExecuteAgent.class.getDeclaredMethod("parseSubTasks", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> tasks = (List<String>) method.invoke(agent,
                "[{\"description\": \"no step key\"}, {\"step\": \"has step\"}]");

        // 缺少 "step" 键的条目应被跳过
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0)).isEqualTo("has step");
    }

    @Test
    void tokenCountingAccumulates() throws Exception {
        PlanExecuteAgent agent = createAgent();
        Method accumulate = PlanExecuteAgent.class.getDeclaredMethod("accumulateTokens",
                io.agentscope.core.model.ChatUsage.class);
        accumulate.setAccessible(true);

        io.agentscope.core.model.ChatUsage usage1 = Mockito.mock(io.agentscope.core.model.ChatUsage.class);
        Mockito.when(usage1.getInputTokens()).thenReturn(100);
        Mockito.when(usage1.getOutputTokens()).thenReturn(50);
        accumulate.invoke(agent, usage1);

        io.agentscope.core.model.ChatUsage usage2 = Mockito.mock(io.agentscope.core.model.ChatUsage.class);
        Mockito.when(usage2.getInputTokens()).thenReturn(200);
        Mockito.when(usage2.getOutputTokens()).thenReturn(80);
        accumulate.invoke(agent, usage2);

        assertThat(agent.getTotalInputTokens()).isEqualTo(300L);
        assertThat(agent.getTotalOutputTokens()).isEqualTo(130L);
    }

    @Test
    void tokenCountingHandlesNullUsage() throws Exception {
        PlanExecuteAgent agent = createAgent();
        Method accumulate = PlanExecuteAgent.class.getDeclaredMethod("accumulateTokens",
                io.agentscope.core.model.ChatUsage.class);
        accumulate.setAccessible(true);

        accumulate.invoke(agent, (Object) null);

        assertThat(agent.getTotalInputTokens()).isEqualTo(0L);
        assertThat(agent.getTotalOutputTokens()).isEqualTo(0L);
    }

    @Test
    void executeStreamReturnsFluxAndHandlesErrorGracefully() {
        // Mock Model 在调用时会抛异常 → executeStream 应通过 onErrorResume 降级为 ERROR chunk
        PlanExecuteAgent agent = createAgent();

        reactor.core.publisher.Flux<io.lumina.agent.model.StreamChunk> flux = agent.executeStream();

        java.util.List<io.lumina.agent.model.StreamChunk> chunks = flux.collectList().block();

        assertThat(chunks).isNotNull();
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).type()).isEqualTo(io.lumina.agent.model.StreamEventType.ERROR);
        assertThat(chunks.get(0).last()).isTrue();
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用 PlanExecuteAgent（Model 和 Toolkit 为 mock，不会真实调用 LLM）
     */
    private PlanExecuteAgent createAgent() {
        Model mockModel = Mockito.mock(Model.class);
        Toolkit mockToolkit = Mockito.mock(Toolkit.class);
        return new PlanExecuteAgent(mockModel, mockToolkit, "test task", "system prompt");
    }
}
