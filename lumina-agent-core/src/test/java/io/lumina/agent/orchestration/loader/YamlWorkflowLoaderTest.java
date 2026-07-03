package io.lumina.agent.orchestration.loader;

import io.lumina.agent.orchestration.model.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * YamlWorkflowLoader 单元测试
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class YamlWorkflowLoaderTest {

    private YamlWorkflowLoader loader;

    @BeforeEach
    void setUp() {
        loader = new YamlWorkflowLoader();
    }

    @Test
    void loadPipelineYaml() {
        String yaml = """
            name: "test-pipeline"
            description: "Test"
            nodes:
              - id: step1
                type: agent
                name: "Step 1"
                agentId: 1
                input: "'hello'"
                outputVar: "result"
              - id: step2
                type: transform
                name: "Transform"
                transformExpr: "#result.toUpperCase()"
                outputVar: "final"
            edges:
              - from: step1
                to: step2
            """;

        WorkflowDefinition def = loader.load(yaml);

        assertThat(def.getName()).isEqualTo("test-pipeline");
        assertThat(def.getNodes()).hasSize(2);
        assertThat(def.getEdges()).hasSize(1);
        assertThat(def.getNodes().get(0)).isInstanceOf(io.lumina.agent.orchestration.model.AgentNode.class);
        assertThat(def.getNodes().get(1)).isInstanceOf(io.lumina.agent.orchestration.model.TransformNode.class);
    }

    @Test
    void loadConditionNode() {
        String yaml = """
            name: "test-condition"
            nodes:
              - id: classify
                type: agent
                agentId: 1
                outputVar: "category"
              - id: route
                type: condition
                expression: "#category == 'refund'"
                trueBranch: refund
                falseBranch: general
              - id: refund
                type: agent
                agentId: 2
              - id: general
                type: agent
                agentId: 3
            edges:
              - from: classify
                to: route
            """;

        WorkflowDefinition def = loader.load(yaml);

        assertThat(def.findNode("route")).isInstanceOf(io.lumina.agent.orchestration.model.ConditionNode.class);
        io.lumina.agent.orchestration.model.ConditionNode route =
                (io.lumina.agent.orchestration.model.ConditionNode) def.findNode("route");
        assertThat(route.getExpression()).isEqualTo("#category == 'refund'");
        assertThat(route.getTrueBranch()).isEqualTo("refund");
    }

    @Test
    void validationRejectsEmptyName() {
        String yaml = """
            name: ""
            nodes:
              - id: a
                type: agent
                agentId: 1
            """;

        assertThatThrownBy(() -> loader.load(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("名称不能为空");
    }

    @Test
    void validationRejectsNoNodes() {
        String yaml = """
            name: "empty"
            nodes: []
            """;

        assertThatThrownBy(() -> loader.load(yaml))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validationRejectsDuplicateNodeIds() {
        String yaml = """
            name: "dup"
            nodes:
              - id: same
                type: agent
                agentId: 1
              - id: same
                type: agent
                agentId: 2
            """;

        assertThatThrownBy(() -> loader.load(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void validationRejectsInvalidEdge() {
        String yaml = """
            name: "bad-edge"
            nodes:
              - id: a
                type: agent
                agentId: 1
            edges:
              - from: a
                to: nonexistent
            """;

        assertThatThrownBy(() -> loader.load(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目标节点不存在");
    }

    @Test
    void toYamlRoundTrip() {
        String yaml = """
            name: "round-trip"
            description: "Round trip test"
            nodes:
              - id: node1
                type: transform
                transformExpr: "#input"
                outputVar: "result"
            """;

        WorkflowDefinition def = loader.load(yaml);
        String serialized = loader.toYaml(def);
        WorkflowDefinition reparsed = loader.load(serialized);

        assertThat(reparsed.getName()).isEqualTo("round-trip");
        assertThat(reparsed.getNodes()).hasSize(1);
    }

    @Test
    void getStartNodesFindsEntry() {
        String yaml = """
            name: "start-test"
            nodes:
              - id: a
                type: agent
                agentId: 1
              - id: b
                type: agent
                agentId: 2
            edges:
              - from: a
                to: b
            """;

        WorkflowDefinition def = loader.load(yaml);

        assertThat(def.getStartNodes()).hasSize(1);
        assertThat(def.getStartNodes().get(0).getId()).isEqualTo("a");
    }
}
