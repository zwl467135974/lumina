package io.lumina.agent.orchestration.script;

import io.lumina.agent.orchestration.engine.AgentExecutionHandler;
import io.lumina.agent.orchestration.model.AutonomyNode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AutonomyScriptEngine 单元测试
 *
 * <p>覆盖沙箱逃逸防护、限额纪律、超时强杀、物化校验与并行语义。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
class AutonomyScriptEngineTest {

    private final AgentExecutionHandler handler = Mockito.mock(AgentExecutionHandler.class);
    private final AutonomyScriptEngine engine = new AutonomyScriptEngine(handler);

    private AutonomyNode node(String script) {
        AutonomyNode node = new AutonomyNode();
        node.setId("auto-1");
        node.setAgentId(9L);
        node.setScript(script);
        return node;
    }

    // ==================== 沙箱 ====================

    @Test
    void sandboxRejectsHostClassAccess() {
        assertThatThrownBy(() -> engine.run(node("return Java.type('java.lang.Runtime')"), "in"))
                .hasMessageContaining("autonomy 脚本执行失败");
        Mockito.verifyNoInteractions(handler);
    }

    @Test
    void inputVariableIsBound() {
        Mockito.when(handler.executeAgent(Mockito.eq(9L), Mockito.eq("回声: 你好"), Mockito.any()))
                .thenReturn("ok");

        Object result = engine.run(node("return agent('回声: ' + input)"), "你好");

        assertThat(result).isEqualTo("ok");
    }

    // ==================== 桥接函数 ====================

    @Test
    void agentCallReturnsHandlerResult() {
        Mockito.when(handler.executeAgent(Mockito.eq(9L), Mockito.anyString(), Mockito.any()))
                .thenReturn("结果A");

        Object result = engine.run(node("return agent('任务A')"), "");

        assertThat(result).isEqualTo("结果A");
    }

    @Test
    void parallelFansOutAndMapsItemFailureToNull() {
        Mockito.when(handler.executeAgent(Mockito.eq(9L), Mockito.eq("好的"), Mockito.any()))
                .thenReturn("成功");
        Mockito.when(handler.executeAgent(Mockito.eq(9L), Mockito.eq("炸了"), Mockito.any()))
                .thenThrow(new RuntimeException("子 Agent 失败"));

        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) engine.run(
                node("return parallel(['好的', '炸了', '好的'])"), "");

        // 单项失败映射 null，其余成功——绝不溶进整体 fatal
        assertThat(result).containsExactly("成功", null, "成功");
    }

    @Test
    void parallelRunsPromptsConcurrently() {
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        Mockito.when(handler.executeAgent(Mockito.eq(9L), Mockito.anyString(), Mockito.any()))
                .thenAnswer(inv -> {
                    int now = concurrent.incrementAndGet();
                    peak.accumulateAndGet(now, Math::max);
                    Thread.sleep(150);
                    concurrent.decrementAndGet();
                    return "ok";
                });
        AutonomyNode n = node("return parallel(['a','b','c','d'])");
        n.setMaxConcurrentAgents(4);

        engine.run(n, "");

        assertThat(peak.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void pipelineStagesTransformItemsSequentially() {
        Object result = engine.run(node(
                "function upper(s){ return s.toUpperCase() }\n" +
                "function bracket(s){ return '[' + s + ']' }\n" +
                "return pipeline(['a','b'], upper, bracket)"), "");

        assertThat(result).isEqualTo(List.of("[A]", "[B]"));
    }

    @Test
    void pipelineMapsFailingItemToNull() {
        Object result = engine.run(node(
                "function boom(s){ if(s === 'b'){ throw new Error('x') } return s }\n" +
                "return pipeline(['a','b'], boom)"), "");

        assertThat(result).isEqualTo(java.util.Arrays.asList("a", null));
    }

    // ==================== 限额与超时纪律 ====================

    @Test
    void totalCallCapFailsLoudly() {
        Mockito.when(handler.executeAgent(Mockito.eq(9L), Mockito.anyString(), Mockito.any()))
                .thenReturn("ok");
        AutonomyNode n = node("agent('1'); agent('2'); return agent('3')");
        n.setMaxTotalAgents(2);

        // cap 触顶必须响亮，绝不静默
        assertThatThrownBy(() -> engine.run(n, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("总量上限");
    }

    @Test
    void infiniteLoopIsKilledByTimeout() {
        AutonomyNode n = node("while(true){}");
        n.setTimeoutSeconds(1);

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> engine.run(n, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("超时");
        // 超时 + 有界宽限 + 强杀应在远小于 30s 内完成
        assertThat(System.currentTimeMillis() - start).isLessThan(20_000);
    }

    // ==================== 物化校验 ====================

    @Test
    void materializesNestedJson() {
        Object result = engine.run(node(
                "return { count: 3, items: ['a', 'b'], nested: { ok: true } }"), "");

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.get("count")).isEqualTo(3);
        assertThat(map.get("items")).isEqualTo(List.of("a", "b"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) map.get("nested");
        assertThat(nested.get("ok")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void rejectsFunctionReturnValue() {
        assertThatThrownBy(() -> engine.run(node("return function f(){}"), ""))
                .hasMessageContaining("不能包含函数");
    }

    @Test
    void rejectsDangerousMemberKeys() {
        // JSON.parse 产生自有 __proto__ 属性（原型污染载荷）：卫兵拒绝或键不外流均可接受
        Object result;
        try {
            result = engine.run(node("return JSON.parse('{\"__proto__\": {\"polluted\": true}, \"a\": 2}')"), "");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("非法属性名");
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map).doesNotContainKey("__proto__");
    }
}
