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
