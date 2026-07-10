package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.expression.SpelExpressionEvaluator;
import io.lumina.agent.orchestration.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultWorkflowEngine 并行执行单元测试
 *
 * <p>覆盖 {@link ParallelNode} 的多分支 fan-out / fan-in 机制：
 * <ul>
 *   <li>分支独立执行，各自设置上下文变量</li>
 *   <li>合并后分支结果以分支名为 key 存入主上下文</li>
 *   <li>分支内新增变量以 {@code branchName_varName} 前缀合并</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class DefaultWorkflowEngineParallelTest {

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
    void parallel_allBranchesComplete() {
        ParallelNode parallel = new ParallelNode();
        parallel.setId("parallel");
        parallel.setName("并行执行");
        parallel.setWaitAll(true);
        parallel.setBranches(List.of(
                makeBranch("branch1", "set-a"),
                makeBranch("branch2", "set-b")
        ));

        TransformNode setA = new TransformNode();
        setA.setId("set-a");
        setA.setName("设置A");
        setA.setTransformExpr("'A'");
        setA.setOutputVar("varA");

        TransformNode setB = new TransformNode();
        setB.setId("set-b");
        setB.setName("设置B");
        setB.setTransformExpr("'B'");
        setB.setOutputVar("varB");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("test-parallel");
        def.setNodes(List.of(parallel, setA, setB));
        def.setEdges(List.of());

        WorkflowContext ctx = engine.execute(def, Map.of());

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object b1 = ctx.getVariable("branch1");
        Object b2 = ctx.getVariable("branch2");
        Object b1VarA = ctx.getVariable("branch1_varA");
        Object b2VarB = ctx.getVariable("branch2_varB");
        assertThat(b1).isEqualTo("A");
        assertThat(b2).isEqualTo("B");
        assertThat(b1VarA).isEqualTo("A");
        assertThat(b2VarB).isEqualTo("B");
    }

    @Test
    void parallel_threeBranchesAllComplete() {
        ParallelNode parallel = new ParallelNode();
        parallel.setId("parallel");
        parallel.setName("三分支并行");
        parallel.setWaitAll(true);
        parallel.setBranches(List.of(
                makeBranch("b1", "s1"),
                makeBranch("b2", "s2"),
                makeBranch("b3", "s3")
        ));

        TransformNode s1 = makeTransformNode("s1", "分支1", "'X'", "v1");
        TransformNode s2 = makeTransformNode("s2", "分支2", "'Y'", "v2");
        TransformNode s3 = makeTransformNode("s3", "分支3", "'Z'", "v3");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("test-parallel-three");
        def.setNodes(List.of(parallel, s1, s2, s3));
        def.setEdges(List.of());

        WorkflowContext ctx = engine.execute(def, Map.of());

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object r1 = ctx.getVariable("b1");
        Object r2 = ctx.getVariable("b2");
        Object r3 = ctx.getVariable("b3");
        Object v1 = ctx.getVariable("b1_v1");
        Object v2 = ctx.getVariable("b2_v2");
        Object v3 = ctx.getVariable("b3_v3");
        assertThat(r1).isEqualTo("X");
        assertThat(r2).isEqualTo("Y");
        assertThat(r3).isEqualTo("Z");
        assertThat(v1).isEqualTo("X");
        assertThat(v2).isEqualTo("Y");
        assertThat(v3).isEqualTo("Z");
    }

    @Test
    void parallel_chainBranchSetsMultipleVars() {
        ParallelNode parallel = new ParallelNode();
        parallel.setId("parallel");
        parallel.setName("链式分支并行");
        parallel.setWaitAll(true);
        parallel.setBranches(List.of(
                makeBranch("alpha", "a-start"),
                makeBranch("beta", "b-start")
        ));

        TransformNode aStart = makeTransformNode("a-start", "Alpha第一步", "'hello'", "greeting");
        TransformNode aSecond = makeTransformNode("a-second", "Alpha第二步", "#greeting + '!'", "greetingEx");
        TransformNode bStart = makeTransformNode("b-start", "Beta第一步", "42", "answer");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("test-parallel-chain");
        def.setNodes(List.of(parallel, aStart, aSecond, bStart));
        def.setEdges(List.of(
                new WorkflowEdge("a-start", "a-second", null)
        ));

        WorkflowContext ctx = engine.execute(def, Map.of());

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object alpha = ctx.getVariable("alpha");
        Object alphaGreeting = ctx.getVariable("alpha_greeting");
        Object alphaGreetingEx = ctx.getVariable("alpha_greetingEx");
        Object beta = ctx.getVariable("beta");
        Object betaAnswer = ctx.getVariable("beta_answer");
        assertThat(alpha).isEqualTo("hello");
        assertThat(alphaGreeting).isEqualTo("hello");
        assertThat(alphaGreetingEx).isEqualTo("hello!");
        assertThat(beta).isEqualTo(42);
        assertThat(betaAnswer).isEqualTo(42);
    }

    // --- Helper methods ---

    private static ParallelNode.ParallelBranch makeBranch(String name, String startNode) {
        ParallelNode.ParallelBranch branch = new ParallelNode.ParallelBranch();
        branch.setName(name);
        branch.setStartNode(startNode);
        return branch;
    }

    private static TransformNode makeTransformNode(String id, String name, String expr, String outputVar) {
        TransformNode node = new TransformNode();
        node.setId(id);
        node.setName(name);
        node.setTransformExpr(expr);
        node.setOutputVar(outputVar);
        return node;
    }

    // --- Mock helpers ---

    static class MockAgentHandler implements AgentExecutionHandler {
        @Override
        public String executeAgent(Long agentId, String task, String conversationUuid) {
            return "default-response";
        }
    }
}
