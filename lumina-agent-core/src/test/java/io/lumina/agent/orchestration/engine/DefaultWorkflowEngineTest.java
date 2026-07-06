package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.expression.SpelExpressionEvaluator;
import io.lumina.agent.orchestration.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DefaultWorkflowEngine 单元测试
 *
 * <p>覆盖顺序执行、条件路由、Transform、Human 暂停、事件监听等核心路径。
 * Agent 节点使用 Mock AgentExecutionHandler。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class DefaultWorkflowEngineTest {

    private DefaultWorkflowEngine engine;
    private MockAgentHandler mockHandler;
    private SpelExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpelExpressionEvaluator();
        mockHandler = new MockAgentHandler();
        List<NodeExecutor> executors = List.of(
                new AgentNodeExecutor(mockHandler, evaluator),
                new ConditionNodeExecutor(evaluator),
                new TransformNodeExecutor(evaluator),
                new HumanNodeExecutor(),
                new ParallelNodeExecutor(),
                new LoopNodeExecutor(evaluator)
        );
        engine = new DefaultWorkflowEngine(executors, evaluator);
    }

    @Test
    void sequentialPipeline() {
        mockHandler.register(1L, "任务: A", "结果A");
        mockHandler.register(2L, "结果A", "结果B");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("test-pipeline");

        AgentNode n1 = new AgentNode();
        n1.setId("a"); n1.setName("节点A"); n1.setAgentId(1L);
        n1.setInput("'任务: A'"); n1.setOutputVar("step1");

        AgentNode n2 = new AgentNode();
        n2.setId("b"); n2.setName("节点B"); n2.setAgentId(2L);
        n2.setInput("#step1"); n2.setOutputVar("step2");

        def.setNodes(List.of(n1, n2));
        def.setEdges(List.of(new WorkflowEdge("a", "b", null)));

        WorkflowContext ctx = engine.execute(def, Map.of());

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object step1 = ctx.getVariable("step1");
        Object step2 = ctx.getVariable("step2");
        assertThat(step1).isEqualTo("结果A");
        assertThat(step2).isEqualTo("结果B");
    }

    @Test
    void conditionRoutingTrue() {
        mockHandler.register(1L, "分析", "refund");
        mockHandler.register(2L, "refund", "退款已处理");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("test-condition");

        AgentNode classify = new AgentNode();
        classify.setId("classify"); classify.setName("分类"); classify.setAgentId(1L);
        classify.setInput("'分析'"); classify.setOutputVar("category");

        ConditionNode route = new ConditionNode();
        route.setId("route"); route.setName("路由");
        route.setExpression("#category.contains('refund')");
        route.setTrueBranch("refund-agent");
        route.setFalseBranch("general-agent");

        AgentNode refund = new AgentNode();
        refund.setId("refund-agent"); refund.setName("退款"); refund.setAgentId(2L);
        refund.setInput("#category"); refund.setOutputVar("answer");

        AgentNode general = new AgentNode();
        general.setId("general-agent"); general.setName("通用"); general.setAgentId(2L);
        general.setInput("#category"); general.setOutputVar("answer");

        def.setNodes(List.of(classify, route, refund, general));
        def.setEdges(List.of(
                new WorkflowEdge("classify", "route", null),
                new WorkflowEdge("route", "general-agent", "#category.contains('refund') == false")
        ));

        WorkflowContext ctx = engine.execute(def, Map.of());

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object answer = ctx.getVariable("answer");
        assertThat(answer).isEqualTo("退款已处理");
        assertThat(ctx.getNodeStatuses().get("general-agent")).isNull();
    }

    @Test
    void transformNode() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("test-transform");

        TransformNode node = new TransformNode();
        node.setId("upper"); node.setName("转大写");
        node.setTransformExpr("#text.toUpperCase()");
        node.setOutputVar("result");

        def.setNodes(List.of(node));
        def.setEdges(List.of());

        WorkflowContext ctx = engine.execute(def, Map.of("text", "hello"));

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object result1 = ctx.getVariable("result");
        assertThat(result1).isEqualTo("HELLO");
    }

    @Test
    void transformTemplate() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("test-template");

        TransformNode node = new TransformNode();
        node.setId("format"); node.setName("格式化");
        node.setTemplate("Name: ${name}, Age: ${age}");
        node.setOutputVar("result");

        def.setNodes(List.of(node));
        def.setEdges(List.of());

        WorkflowContext ctx = engine.execute(def, Map.of("name", "Alice", "age", "30"));

        Object result2 = ctx.getVariable("result");
        assertThat(result2).isEqualTo("Name: Alice, Age: 30");
    }

    @Test
    void humanNodePausesWorkflow() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("test-human");

        HumanNode node = new HumanNode();
        node.setId("approve"); node.setName("审批");
        node.setPrompt("请审批"); node.setDecisionVar("decision");

        def.setNodes(List.of(node));
        def.setEdges(List.of());

        WorkflowContext ctx = engine.execute(def, Map.of());

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.PAUSED);
        assertThat(ctx.getCurrentNodeId()).isEqualTo("approve");
    }

    @Test
    void eventListenerReceivesEvents() {
        mockHandler.register(1L, "test", "output");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("test-events");

        AgentNode node = new AgentNode();
        node.setId("agent1"); node.setName("Agent1"); node.setAgentId(1L);
        node.setInput("'test'"); node.setOutputVar("out");

        def.setNodes(List.of(node));
        def.setEdges(List.of());

        EventCollector collector = new EventCollector();
        engine.addListener(collector);

        engine.execute(def, Map.of());

        assertThat(collector.started).containsExactly("agent1");
        assertThat(collector.completed).containsExactly("agent1");
        assertThat(collector.workflowCompleted).isTrue();
    }

    @Test
    void emptyWorkflowReturnsFailedStatus() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("empty");
        def.setNodes(List.of());
        def.setEdges(List.of());

        WorkflowContext ctx = engine.execute(def, Map.of());
        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    }

    @Test
    void outputEvaluation() {
        mockHandler.register(1L, "input", "final-value");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("test-outputs");

        AgentNode node = new AgentNode();
        node.setId("agent1"); node.setName("Agent1"); node.setAgentId(1L);
        node.setInput("'input'"); node.setOutputVar("raw");

        def.setNodes(List.of(node));
        def.setEdges(List.of());
        def.setOutputs(new WorkflowDefinition.MapEntry[]{
                new WorkflowDefinition.MapEntry("final", "#raw")
        });

        WorkflowContext ctx = engine.execute(def, Map.of());

        Object finalVal = ctx.getVariable("final");
        assertThat(finalVal).isEqualTo("final-value");
    }

    // ==================== 协作模式测试 ====================

    @Test
    /**
     * Pipeline 模式：Agent A → Agent B → Agent C 串行流水线
     * 每个前驱的输出是后继的输入
     */
    void collaborationPipelineThreeStages() {
        mockHandler.register(1L, "raw-data", "cleaned");
        mockHandler.register(2L, "cleaned", "analyzed");
        mockHandler.register(3L, "analyzed", "report");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("pipeline-test");

        AgentNode s1 = new AgentNode();
        s1.setId("stage-1"); s1.setName("清洗"); s1.setAgentId(1L);
        s1.setInput("'raw-data'"); s1.setOutputVar("data1");

        AgentNode s2 = new AgentNode();
        s2.setId("stage-2"); s2.setName("分析"); s2.setAgentId(2L);
        s2.setInput("#data1"); s2.setOutputVar("data2");

        AgentNode s3 = new AgentNode();
        s3.setId("stage-3"); s3.setName("报告"); s3.setAgentId(3L);
        s3.setInput("#data2"); s3.setOutputVar("final");

        def.setNodes(List.of(s1, s2, s3));
        def.setEdges(List.of(
                new WorkflowEdge("stage-1", "stage-2", null),
                new WorkflowEdge("stage-2", "stage-3", null)
        ));

        WorkflowContext ctx = engine.execute(def, Map.of());

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object d1 = ctx.getVariable("data1");
        Object d2 = ctx.getVariable("data2");
        Object df = ctx.getVariable("final");
        assertThat(d1).isEqualTo("cleaned");
        assertThat(d2).isEqualTo("analyzed");
        assertThat(df).isEqualTo("report");
    }

    @Test
    /**
     * Supervisor-Worker 模式：主管分解 → 多 Worker 串行执行 → 主管汇总
     * （简化版，不含并行节点）
     */
    void collaborationSupervisorWorker() {
        mockHandler.register(1L, "分解任务: 复杂问题", "子任务A");
        mockHandler.register(2L, "子任务A", "结果A");
        mockHandler.register(1L, "汇总: 结果A", "最终汇总");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("supervisor-worker-test");

        AgentNode supervisor1 = new AgentNode();
        supervisor1.setId("supervisor-decompose"); supervisor1.setName("主管分解");
        supervisor1.setAgentId(1L);
        supervisor1.setInput("'分解任务: 复杂问题'");
        supervisor1.setOutputVar("subtask");

        AgentNode worker = new AgentNode();
        worker.setId("worker"); worker.setName("Worker 执行");
        worker.setAgentId(2L);
        worker.setInput("#subtask"); worker.setOutputVar("workerResult");

        AgentNode supervisor2 = new AgentNode();
        supervisor2.setId("supervisor-aggregate"); supervisor2.setName("主管汇总");
        supervisor2.setAgentId(1L);
        supervisor2.setInput("'汇总: ' + #workerResult");
        supervisor2.setOutputVar("finalResult");

        def.setNodes(List.of(supervisor1, worker, supervisor2));
        def.setEdges(List.of(
                new WorkflowEdge("supervisor-decompose", "worker", null),
                new WorkflowEdge("worker", "supervisor-aggregate", null)
        ));

        WorkflowContext ctx = engine.execute(def, Map.of());

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object st = ctx.getVariable("subtask");
        Object wr = ctx.getVariable("workerResult");
        Object fr = ctx.getVariable("finalResult");
        assertThat(st).isEqualTo("子任务A");
        assertThat(wr).isEqualTo("结果A");
        assertThat(fr).isEqualTo("最终汇总");
    }

    @Test
    /**
     * Router 模式：分类 Agent 判断意图 → 路由到不同专业 Agent（测试 false 分支）
     */
    void collaborationRouterFalseBranch() {
        mockHandler.register(1L, "用户问题", "general");
        mockHandler.register(3L, "general", "通用回复");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("router-test");

        AgentNode classify = new AgentNode();
        classify.setId("classify"); classify.setName("分类");
        classify.setAgentId(1L);
        classify.setInput("'用户问题'"); classify.setOutputVar("category");

        ConditionNode route = new ConditionNode();
        route.setId("route"); route.setName("路由");
        route.setExpression("#category.contains('refund')");
        route.setTrueBranch("refund-agent");
        route.setFalseBranch("general-agent");

        AgentNode refund = new AgentNode();
        refund.setId("refund-agent"); refund.setName("退款处理");
        refund.setAgentId(2L);
        refund.setInput("#category"); refund.setOutputVar("answer");

        AgentNode general = new AgentNode();
        general.setId("general-agent"); general.setName("通用处理");
        general.setAgentId(3L);
        general.setInput("#category"); general.setOutputVar("answer");

        def.setNodes(List.of(classify, route, refund, general));
        def.setEdges(List.of(
                new WorkflowEdge("classify", "route", null),
                new WorkflowEdge("route", "general-agent", "#category.contains('refund') == false")
        ));

        WorkflowContext ctx = engine.execute(def, Map.of());

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object ans = ctx.getVariable("answer");
        assertThat(ans).isEqualTo("通用回复");
        assertThat(ctx.getNodeStatuses().get("refund-agent")).isNull();
        assertThat(ctx.getNodeStatuses().get("general-agent")).isEqualTo(WorkflowNodeStatus.COMPLETED);
    }

    @Test
    /**
     * Debate 模式：Agent A 观点 → Agent B 反驳 → Agent C 裁判综合
     */
    void collaborationDebate() {
        mockHandler.register(1L, "讨论话题: AI 安全", "正方观点");
        mockHandler.register(2L, "正方观点", "反方观点");
        mockHandler.register(3L, "正方观点 vs 反方观点", "裁判结论");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("debate-test");

        AgentNode proponent = new AgentNode();
        proponent.setId("proponent"); proponent.setName("正方");
        proponent.setAgentId(1L);
        proponent.setInput("'讨论话题: AI 安全'");
        proponent.setOutputVar("proArg");

        AgentNode opponent = new AgentNode();
        opponent.setId("opponent"); opponent.setName("反方");
        opponent.setAgentId(2L);
        opponent.setInput("#proArg"); opponent.setOutputVar("conArg");

        AgentNode judge = new AgentNode();
        judge.setId("judge"); judge.setName("裁判");
        judge.setAgentId(3L);
        judge.setInput("#proArg + ' vs ' + #conArg");
        judge.setOutputVar("verdict");

        def.setNodes(List.of(proponent, opponent, judge));
        def.setEdges(List.of(
                new WorkflowEdge("proponent", "opponent", null),
                new WorkflowEdge("opponent", "judge", null)
        ));

        WorkflowContext ctx = engine.execute(def, Map.of());

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object pa = ctx.getVariable("proArg");
        Object ca = ctx.getVariable("conArg");
        Object vd = ctx.getVariable("verdict");
        assertThat(pa).isEqualTo("正方观点");
        assertThat(ca).isEqualTo("反方观点");
        assertThat(vd).isEqualTo("裁判结论");
    }

    @Test
    /**
     * Human-in-the-Loop 模式：Agent 执行 → 人工暂停 → 验证暂停上下文
     */
    void collaborationHumanInTheLoopPausesWithContext() {
        mockHandler.register(1L, "准备审批材料", "材料已准备");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("hitl-test");

        AgentNode prepare = new AgentNode();
        prepare.setId("prepare"); prepare.setName("准备");
        prepare.setAgentId(1L);
        prepare.setInput("'准备审批材料'");
        prepare.setOutputVar("material");

        HumanNode approve = new HumanNode();
        approve.setId("approve"); approve.setName("审批");
        approve.setPrompt("请审批以下材料: " + "#material");
        approve.setDecisionVar("approval");

        AgentNode execute = new AgentNode();
        execute.setId("execute"); execute.setName("执行");
        execute.setAgentId(1L);
        execute.setInput("#approval"); execute.setOutputVar("result");

        def.setNodes(List.of(prepare, approve, execute));
        def.setEdges(List.of(
                new WorkflowEdge("prepare", "approve", null),
                new WorkflowEdge("approve", "execute", null)
        ));

        WorkflowContext ctx = engine.execute(def, Map.of());

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.PAUSED);
        assertThat(ctx.getCurrentNodeId()).isEqualTo("approve");
        Object mat = ctx.getVariable("material");
        Object appr = ctx.getVariable("approval");
        assertThat(mat).isEqualTo("材料已准备");
        assertThat(appr).isEqualTo("__WAITING__");
        assertThat(ctx.getNodeStatuses().get("prepare")).isEqualTo(WorkflowNodeStatus.COMPLETED);
        assertThat(ctx.getNodeStatuses().get("execute")).isNull();
    }

    @Test
    /**
     * Human-in-the-Loop 恢复：暂停后注入决策 → 继续执行后续节点
     */
    void collaborationHumanInTheLoopResume() {
        mockHandler.register(1L, "'准备'", "材料已准备");
        mockHandler.register(2L, "approved", "已执行");

        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("hitl-resume-test");

        AgentNode prepare = new AgentNode();
        prepare.setId("prepare"); prepare.setName("准备");
        prepare.setAgentId(1L);
        prepare.setInput("'准备'");
        prepare.setOutputVar("material");

        HumanNode approve = new HumanNode();
        approve.setId("approve"); approve.setName("审批");
        approve.setDecisionVar("decision");

        AgentNode executeNode = new AgentNode();
        executeNode.setId("execute"); executeNode.setName("执行");
        executeNode.setAgentId(2L);
        executeNode.setInput("#decision"); executeNode.setOutputVar("result");

        def.setNodes(List.of(prepare, approve, executeNode));
        def.setEdges(List.of(
                new WorkflowEdge("prepare", "approve", null),
                new WorkflowEdge("approve", "execute", null)
        ));

        WorkflowContext ctx = engine.execute(def, Map.of());

        assertThat(ctx.getStatus()).isEqualTo(WorkflowStatus.PAUSED);

        WorkflowContext resumed = engine.resume(def, ctx, "approved");

        assertThat(resumed.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        Object result = resumed.getVariable("result");
        assertThat(result).isEqualTo("已执行");
    }

    // --- Mock helpers ---

    static class MockAgentHandler implements AgentExecutionHandler {
        private final java.util.Map<String, String> responses = new java.util.HashMap<>();

        void register(Long agentId, String task, String response) {
            responses.put(agentId + ":" + task, response);
        }

        @Override
        public String executeAgent(Long agentId, String task, String conversationUuid) {
            String key = agentId + ":" + task;
            String resp = responses.get(key);
            if (resp == null) {
                return "default-response";
            }
            return resp;
        }
    }

    static class EventCollector implements WorkflowEventListener {
        List<String> started = new java.util.ArrayList<>();
        List<String> completed = new java.util.ArrayList<>();
        List<String> failed = new java.util.ArrayList<>();
        boolean workflowCompleted = false;
        boolean workflowFailed = false;

        @Override
        public void onNodeStarted(String nodeId, String nodeName, WorkflowContext ctx) {
            started.add(nodeId);
        }

        @Override
        public void onNodeCompleted(String nodeId, Object result, long durationMs) {
            completed.add(nodeId);
        }

        @Override
        public void onNodeFailed(String nodeId, Throwable error) {
            failed.add(nodeId);
        }

        @Override
        public void onWorkflowCompleted(WorkflowContext ctx) {
            workflowCompleted = true;
        }

        @Override
        public void onWorkflowFailed(WorkflowContext ctx, String error) {
            workflowFailed = true;
        }
    }
}
