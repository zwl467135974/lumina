package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 工作流节点执行器单元测试
 *
 * <p>覆盖 4 个 NodeExecutor 实现的核心逻辑：
 * AgentNode / ConditionNode / HumanNode / TransformNode。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@ExtendWith(MockitoExtension.class)
class NodeExecutorTest {

    @Mock
    private AgentExecutionHandler agentHandler;

    @Mock
    private ExpressionEvaluator expressionEvaluator;

    // ==================== AgentNodeExecutor ====================

    @Test
    void agentNodeSupportsCorrectType() {
        AgentNodeExecutor executor = new AgentNodeExecutor(agentHandler, expressionEvaluator);

        assertThat(executor.supports(new AgentNode())).isTrue();
        assertThat(executor.supports(new ConditionNode())).isFalse();
        assertThat(executor.supports(new TransformNode())).isFalse();
    }

    @Test
    void agentNodeExecuteReturnsHandlerResult() {
        AgentNode node = new AgentNode();
        node.setId("agent-1");
        node.setAgentId(42L);
        node.setInput("执行任务");
        node.setConversationUuid("conv-1");

        WorkflowContext ctx = new WorkflowContext();

        when(expressionEvaluator.evaluate(eq("执行任务"), anyMap())).thenReturn("执行任务");
        when(agentHandler.executeAgent(42L, "执行任务", "conv-1")).thenReturn("任务完成");

        AgentNodeExecutor executor = new AgentNodeExecutor(agentHandler, expressionEvaluator);
        String result = (String) executor.execute(node, ctx);

        assertThat(result).isEqualTo("任务完成");
    }

    @Test
    void agentNodeExecuteWithNullInputPassesEmptyString() {
        AgentNode node = new AgentNode();
        node.setId("agent-2");
        node.setAgentId(10L);
        node.setInput(null);

        WorkflowContext ctx = new WorkflowContext();

        when(agentHandler.executeAgent(eq(10L), anyString(), isNull())).thenReturn("OK");

        AgentNodeExecutor executor = new AgentNodeExecutor(agentHandler, expressionEvaluator);
        String result = (String) executor.execute(node, ctx);

        assertThat(result).isEqualTo("OK");
    }

    // ==================== ConditionNodeExecutor ====================

    @Test
    void conditionNodeSupportsCorrectType() {
        ConditionNodeExecutor executor = new ConditionNodeExecutor(expressionEvaluator);

        assertThat(executor.supports(new ConditionNode())).isTrue();
        assertThat(executor.supports(new AgentNode())).isFalse();
    }

    @Test
    void conditionNodeBinaryTrueBranch() {
        ConditionNode node = new ConditionNode();
        node.setId("cond-1");
        node.setExpression("x > 5");
        node.setTrueBranch("next-true");
        node.setFalseBranch("next-false");

        when(expressionEvaluator.evaluateBoolean(eq("x > 5"), anyMap())).thenReturn(true);

        ConditionNodeExecutor executor = new ConditionNodeExecutor(expressionEvaluator);
        String result = (String) executor.execute(node, new WorkflowContext());

        assertThat(result).isEqualTo("route:next-true");
    }

    @Test
    void conditionNodeBinaryFalseBranch() {
        ConditionNode node = new ConditionNode();
        node.setId("cond-2");
        node.setExpression("x > 5");
        node.setTrueBranch("next-true");
        node.setFalseBranch("next-false");

        when(expressionEvaluator.evaluateBoolean(eq("x > 5"), anyMap())).thenReturn(false);

        ConditionNodeExecutor executor = new ConditionNodeExecutor(expressionEvaluator);
        String result = (String) executor.execute(node, new WorkflowContext());

        assertThat(result).isEqualTo("route:next-false");
    }

    @Test
    void conditionNodeMultiBranchFirstMatchWins() {
        ConditionNode.Branch b1 = new ConditionNode.Branch();
        b1.setCondition("a == 1");
        b1.setTo("branch-a");
        ConditionNode.Branch b2 = new ConditionNode.Branch();
        b2.setCondition("b == 2");
        b2.setTo("branch-b");

        ConditionNode node = new ConditionNode();
        node.setId("cond-3");
        node.setBranches(List.of(b1, b2));

        // 第一个分支匹配
        when(expressionEvaluator.evaluateBoolean(eq("a == 1"), anyMap())).thenReturn(true);

        ConditionNodeExecutor executor = new ConditionNodeExecutor(expressionEvaluator);
        String result = (String) executor.execute(node, new WorkflowContext());

        assertThat(result).isEqualTo("route:branch-a");
        // 第二个分支不应该被求值（短路）
        verify(expressionEvaluator, times(1)).evaluateBoolean(anyString(), anyMap());
    }

    @Test
    void conditionNodeMultiBranchNoMatchReturnsNull() {
        ConditionNode.Branch b1 = new ConditionNode.Branch();
        b1.setCondition("a == 1");
        b1.setTo("branch-a");

        ConditionNode node = new ConditionNode();
        node.setId("cond-4");
        node.setBranches(List.of(b1));

        when(expressionEvaluator.evaluateBoolean(eq("a == 1"), anyMap())).thenReturn(false);

        ConditionNodeExecutor executor = new ConditionNodeExecutor(expressionEvaluator);
        Object result = executor.execute(node, new WorkflowContext());

        assertThat(result).isNull();
    }

    // ==================== HumanNodeExecutor ====================

    @Test
    void humanNodeSupportsCorrectType() {
        HumanNodeExecutor executor = new HumanNodeExecutor();

        assertThat(executor.supports(new HumanNode())).isTrue();
        assertThat(executor.supports(new AgentNode())).isFalse();
    }

    @Test
    void humanNodeExecuteThrowsApprovalRequired() {
        HumanNode node = new HumanNode();
        node.setId("human-1");
        node.setPrompt("请审批此操作");
        node.setDecisionVar("approval");

        HumanNodeExecutor executor = new HumanNodeExecutor();

        assertThatThrownBy(() -> executor.execute(node, new WorkflowContext()))
                .isInstanceOf(HumanNodeExecutor.HumanApprovalRequiredException.class)
                .satisfies(ex -> {
                    HumanNodeExecutor.HumanApprovalRequiredException hae =
                            (HumanNodeExecutor.HumanApprovalRequiredException) ex;
                    assertThat(hae.getNodeId()).isEqualTo("human-1");
                    assertThat(hae.getPrompt()).isEqualTo("请审批此操作");
                    assertThat(hae.getDecisionVar()).isEqualTo("approval");
                });
    }

    @Test
    void humanNodeExceptionMessageContainsNodeId() {
        HumanNode node = new HumanNode();
        node.setId("human-2");
        node.setPrompt("确认");

        HumanNodeExecutor executor = new HumanNodeExecutor();

        assertThatThrownBy(() -> executor.execute(node, new WorkflowContext()))
                .hasMessageContaining("human-2");
    }

    @Test
    void humanNodeDefaultDecisionVar() {
        HumanNode node = new HumanNode();
        node.setId("human-3");
        node.setPrompt("审批");
        // decisionVar 不设置，默认应为 "decision"

        HumanNodeExecutor executor = new HumanNodeExecutor();

        assertThatThrownBy(() -> executor.execute(node, new WorkflowContext()))
                .isInstanceOf(HumanNodeExecutor.HumanApprovalRequiredException.class)
                .satisfies(ex -> {
                    HumanNodeExecutor.HumanApprovalRequiredException hae =
                            (HumanNodeExecutor.HumanApprovalRequiredException) ex;
                    assertThat(hae.getDecisionVar()).isEqualTo("decision");
                });
    }

    // ==================== TransformNodeExecutor ====================

    @Test
    void transformNodeSupportsCorrectType() {
        TransformNodeExecutor executor = new TransformNodeExecutor(expressionEvaluator);

        assertThat(executor.supports(new TransformNode())).isTrue();
        assertThat(executor.supports(new ConditionNode())).isFalse();
    }

    @Test
    void transformNodeSpelModeEvaluatesExpression() {
        TransformNode node = new TransformNode();
        node.setId("transform-1");
        node.setTransformExpr("#result.toUpperCase()");

        when(expressionEvaluator.evaluate(eq("#result.toUpperCase()"), anyMap()))
                .thenReturn("HELLO");

        TransformNodeExecutor executor = new TransformNodeExecutor(expressionEvaluator);
        Object result = executor.execute(node, new WorkflowContext());

        assertThat(result).isEqualTo("HELLO");
    }

    @Test
    void transformNodeTemplateModeReplacesVariables() {
        TransformNode node = new TransformNode();
        node.setId("transform-2");
        node.setTemplate("你好，${name}！你有${count}条消息。");

        WorkflowContext ctx = new WorkflowContext();
        ctx.setNodeResult("name", "张三");
        ctx.setNodeResult("count", 5);

        // 模板模式不需要 evaluator（transformExpr 为 null）
        TransformNodeExecutor executor = new TransformNodeExecutor(expressionEvaluator);
        Object result = executor.execute(node, ctx);

        assertThat(result).isEqualTo("你好，张三！你有5条消息。");
    }

    @Test
    void transformNodeTemplateWithNullValueReplacedByEmpty() {
        TransformNode node = new TransformNode();
        node.setId("transform-3");
        node.setTemplate("值: [${missing}]");

        WorkflowContext ctx = new WorkflowContext();
        ctx.setNodeResult("missing", null);

        TransformNodeExecutor executor = new TransformNodeExecutor(expressionEvaluator);
        Object result = executor.execute(node, ctx);

        assertThat(result).isEqualTo("值: []");
    }

    @Test
    void transformNodeUnmatchedPlaceholderPreserved() {
        TransformNode node = new TransformNode();
        node.setId("transform-4");
        node.setTemplate("保留: ${unknown}");

        TransformNodeExecutor executor = new TransformNodeExecutor(expressionEvaluator);
        Object result = executor.execute(node, new WorkflowContext());

        // 没有匹配的变量，${unknown} 原样保留
        assertThat(result).isEqualTo("保留: ${unknown}");
    }

    @Test
    void transformNodeBothNullReturnsNull() {
        TransformNode node = new TransformNode();
        node.setId("transform-5");
        node.setTransformExpr(null);
        node.setTemplate(null);

        TransformNodeExecutor executor = new TransformNodeExecutor(expressionEvaluator);
        Object result = executor.execute(node, new WorkflowContext());

        assertThat(result).isNull();
    }

    @Test
    void transformNodeSpelTakesPriorityOverTemplate() {
        TransformNode node = new TransformNode();
        node.setId("transform-6");
        node.setTransformExpr("#spel");
        node.setTemplate("${var}");

        when(expressionEvaluator.evaluate(eq("#spel"), anyMap())).thenReturn("spel-result");

        TransformNodeExecutor executor = new TransformNodeExecutor(expressionEvaluator);
        Object result = executor.execute(node, new WorkflowContext());

        assertThat(result).isEqualTo("spel-result");
    }

    // ==================== WorkflowContext 纯逻辑 ====================

    @Test
    void workflowContextVariableGetSetRoundTrip() {
        WorkflowContext ctx = new WorkflowContext();
        ctx.setVariable("key", "value");

        assertThat((String) ctx.getVariable("key")).isEqualTo("value");
    }

    @Test
    void workflowContextToEvaluationRootMergesVariablesAndResults() {
        WorkflowContext ctx = new WorkflowContext();
        ctx.setVariable("var1", "v1");
        ctx.setNodeResult("result1", "r1");

        Map<String, Object> root = ctx.toEvaluationRoot();

        assertThat(root).containsEntry("var1", "v1");
        assertThat(root).containsEntry("result1", "r1");
    }

    @Test
    void workflowContextToEvaluationRootIsDefensiveCopy() {
        WorkflowContext ctx = new WorkflowContext();
        ctx.setVariable("key", "original");

        Map<String, Object> root = ctx.toEvaluationRoot();
        root.put("key", "modified");

        // 修改 root 不影响原 context
        assertThat((String) ctx.getVariable("key")).isEqualTo("original");
    }
}
