package io.lumina.agent.orchestration.flowable;

import io.lumina.agent.orchestration.loader.YamlWorkflowLoader;
import io.lumina.agent.orchestration.model.WorkflowDefinition;
import org.flowable.bpmn.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FlowableBpmnConverter 单元测试
 *
 * <p>验证 Lumina 工作流 YAML 定义 → Flowable BPMN 模型的转换正确性。
 * 纯转换逻辑，不依赖 Flowable 引擎运行时。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
class FlowableBpmnConverterTest {

    private FlowableBpmnConverter converter;
    private YamlWorkflowLoader loader;

    @BeforeEach
    void setUp() {
        converter = new FlowableBpmnConverter();
        loader = new YamlWorkflowLoader();
    }

    @Test
    void convertSimplePipeline() {
        WorkflowDefinition def = load("""
            name: "test-pipeline"
            nodes:
              - id: step1
                type: agent
                agentId: 1
                input: "'hello'"
                outputVar: "result"
              - id: step2
                type: transform
                transformExpr: "#result.toUpperCase()"
                outputVar: "final"
            edges:
              - from: step1
                to: step2
            """);

        BpmnModel model = converter.convert(def);

        assertThat(model).isNotNull();
        assertThat(model.getProcesses()).hasSize(1);
        org.flowable.bpmn.model.Process process = model.getProcesses().get(0);
        assertThat(process).isNotNull();
    }

    @Test
    void convertAlwaysHasStartAndEndEvents() {
        WorkflowDefinition def = load("""
            name: "test-events"
            nodes:
              - id: n1
                type: agent
                agentId: 1
                outputVar: "r"
            """);

        BpmnModel model = converter.convert(def);
        org.flowable.bpmn.model.Process process = model.getProcesses().get(0);

        assertThat(process.findFlowElementsOfType(StartEvent.class)).isNotEmpty();
        assertThat(process.findFlowElementsOfType(EndEvent.class)).isNotEmpty();
    }

    @Test
    void convertPipelineWithAgentNode() {
        WorkflowDefinition def = load("""
            name: "agent-flow"
            nodes:
              - id: a1
                type: agent
                agentId: 42
                outputVar: "out"
            """);

        BpmnModel model = converter.convert(def);
        org.flowable.bpmn.model.Process process = model.getProcesses().get(0);

        assertThat(process.findFlowElementsOfType(ServiceTask.class)).isNotEmpty();
    }

    @Test
    void convertPipelineWithConditionNode() {
        WorkflowDefinition def = load("""
            name: "condition-flow"
            nodes:
              - id: cond
                type: condition
                expression: "result == 'ok'"
                trueBranch: "yes"
                falseBranch: "no"
              - id: yes
                type: agent
                agentId: 1
                outputVar: "y"
              - id: no
                type: agent
                agentId: 2
                outputVar: "n"
            """);

        BpmnModel model = converter.convert(def);
        org.flowable.bpmn.model.Process process = model.getProcesses().get(0);

        assertThat(process.findFlowElementsOfType(ExclusiveGateway.class)).isNotEmpty();
    }

    @Test
    void convertPreservesNamespace() {
        WorkflowDefinition def = load("""
            name: "ns-test"
            nodes:
              - id: n1
                type: agent
                agentId: 1
                outputVar: "r"
            """);

        BpmnModel model = converter.convert(def);

        assertThat(model.getNamespaces())
                .containsEntry(FlowableBpmnConverter.LUMINA_NS_PREFIX, FlowableBpmnConverter.LUMINA_NS);
    }

    @Test
    void convertSanitizesProcessId() {
        WorkflowDefinition def = load("""
            name: "my.workflow.v2"
            nodes:
              - id: n1
                type: agent
                agentId: 1
                outputVar: "r"
            """);

        BpmnModel model = converter.convert(def);

        assertThat(model.getProcesses()).hasSize(1);
        String processId = model.getProcesses().get(0).getId();
        assertThat(processId).doesNotContain(".");
    }

    @Test
    void convertMultipleNodesCreatesFlowElements() {
        WorkflowDefinition def = load("""
            name: "multi-node"
            nodes:
              - id: a1
                type: agent
                agentId: 1
                outputVar: "r1"
              - id: a2
                type: agent
                agentId: 2
                outputVar: "r2"
            edges:
              - from: a1
                to: a2
            """);

        BpmnModel model = converter.convert(def);
        org.flowable.bpmn.model.Process process = model.getProcesses().get(0);

        assertThat(process.getFlowElements().size()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void convertCreatesSequenceFlows() {
        WorkflowDefinition def = load("""
            name: "flow-test"
            nodes:
              - id: a1
                type: agent
                agentId: 1
                outputVar: "r"
            """);

        BpmnModel model = converter.convert(def);
        org.flowable.bpmn.model.Process process = model.getProcesses().get(0);

        assertThat(process.findFlowElementsOfType(SequenceFlow.class)).isNotEmpty();
    }

    // ==================== 辅助方法 ====================

    private WorkflowDefinition load(String yaml) {
        return loader.load(yaml);
    }
}
