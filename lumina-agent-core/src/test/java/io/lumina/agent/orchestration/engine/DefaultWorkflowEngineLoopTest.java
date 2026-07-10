package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.expression.SpelExpressionEvaluator;
import io.lumina.agent.orchestration.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultWorkflowEngine 循环功能单元测试
 *
 * <p>覆盖 {@link LoopNode} 的两种模式：
 * <ul>
 *   <li>条件循环 — {@code conditionExpr} 为 true 时重复执行循环体</li>
 *   <li>集合遍历 — {@code iterateVar} 引用集合，逐元素执行循环体</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class DefaultWorkflowEngineLoopTest {

    private DefaultWorkflowEngine engine;
    private SpelExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpelExpressionEvaluator();
        List<NodeExecutor> executors = List.of(
                new AgentNodeExecutor(new MockAgentHandler(), evaluator),
                new ConditionNodeExecutor(evaluator),
                new TransformNodeExecutor(evaluator),
                new HumanNodeExecutor(),
                new ParallelNodeExecutor(),
                new LoopNodeExecutor(evaluator)
        );
        engine = new DefaultWorkflowEngine(executors, evaluator);
    }

    @Test
    void conditionLoop_incrementsUntilConditionFalse() {
        TransformNode init = new TransformNode();
        init.setId("init");
        init.setName("初始化计数器");
        init.setTransformExpr("0");
        init.setOutputVar("counter");

        LoopNode loop = new LoopNode();
        loop.setId("loop");
        loop.setName("条件循环");
        loop.setConditionExpr("#counter < 3");
        loop.setLoopTarget("increment");
        loop.setExitTarget("done");

        TransformNode increment = new TransformNode();
        increment.setId("increment");
        increment.setName("自增");
        increment.setTransformExpr("#counter + 1");
        increment.setOutputVar("counter");

        TransformNode done = new TransformNode();
        done.setId("done");
        done.setName("完成");
        done.setTransformExpr("'finished'");
        done.setOutputVar("result");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("test-condition-loop");
        def.setNodes(List.of(init, loop, increment, done));
        def.setEdges(List.of(
                new WorkflowEdge("init", "loop", null)
        ));

        WorkflowContext ctx = engine.execute(def, Map.of());

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object counter = ctx.getVariable("counter");
        Object result = ctx.getVariable("result");
        assertThat(counter).isEqualTo(3);
        assertThat(result).isEqualTo("finished");
    }

    @Test
    void iterationLoop_processesAllItems() {
        LoopNode loop = new LoopNode();
        loop.setId("loop");
        loop.setName("遍历集合");
        loop.setIterateVar("#items");
        loop.setItemVar("item");
        loop.setLoopTarget("process");
        loop.setExitTarget("done");

        TransformNode process = new TransformNode();
        process.setId("process");
        process.setName("处理元素");
        process.setTransformExpr("#item");
        process.setOutputVar("lastItem");

        TransformNode done = new TransformNode();
        done.setId("done");
        done.setName("完成");
        done.setTransformExpr("'iterated'");
        done.setOutputVar("result");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("test-iteration-loop");
        def.setNodes(List.of(loop, process, done));
        def.setEdges(List.of());

        WorkflowContext ctx = engine.execute(def, Map.of(
                "items", List.of(10, 20, 30)
        ));

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object lastItem = ctx.getVariable("lastItem");
        Object result = ctx.getVariable("result");
        assertThat(lastItem).isEqualTo(30);
        assertThat(result).isEqualTo("iterated");
    }

    @Test
    void conditionLoop_initiallyFalse_skipsBody() {
        LoopNode loop = new LoopNode();
        loop.setId("loop");
        loop.setName("条件循环");
        loop.setConditionExpr("#counter < 3");
        loop.setLoopTarget("increment");
        loop.setExitTarget("done");

        TransformNode increment = new TransformNode();
        increment.setId("increment");
        increment.setName("自增");
        increment.setTransformExpr("#counter + 1");
        increment.setOutputVar("counter");

        TransformNode done = new TransformNode();
        done.setId("done");
        done.setName("完成");
        done.setTransformExpr("'skipped'");
        done.setOutputVar("result");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("test-condition-loop-skip");
        def.setNodes(List.of(loop, increment, done));
        def.setEdges(List.of());

        WorkflowContext ctx = engine.execute(def, Map.of("counter", 5));

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object counter = ctx.getVariable("counter");
        Object result = ctx.getVariable("result");
        assertThat(counter).isEqualTo(5);
        assertThat(result).isEqualTo("skipped");
    }

    // --- Mock helpers ---

    static class MockAgentHandler implements AgentExecutionHandler {
        @Override
        public String executeAgent(Long agentId, String task, String conversationUuid) {
            return "default-response";
        }
    }
}
