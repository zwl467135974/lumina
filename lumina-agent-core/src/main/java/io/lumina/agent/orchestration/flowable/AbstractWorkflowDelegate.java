package io.lumina.agent.orchestration.flowable;

import io.lumina.agent.orchestration.model.WorkflowDefinition;
import io.lumina.agent.orchestration.model.WorkflowNode;
import io.lumina.agent.util.JsonUtils;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

import java.util.List;
import java.util.Map;

/**
 * Flowable JavaDelegate 基类 — 从 BPMN 扩展元素中反序列化原始 YAML 节点定义
 *
 * <p>转换器将 {@link WorkflowNode} 序列化为 JSON 存入 BPMN extension element，
 * Delegate 执行时取出并反序列化，获得完整的节点属性（agentId、input、outputVar 等）。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public abstract class AbstractWorkflowDelegate implements JavaDelegate {

    protected static final String NODE_DEFINITION_EXT = "nodeDefinition";
    protected static final String VAR_WORKFLOW_DEFINITION = "__workflowDefinition__";

    /**
     * 从当前 BPMN 元素的扩展元素中取出节点定义并反序列化
     *
     * @param execution    Flowable 执行上下文
     * @param expectedType 期望的节点子类型
     * @return 反序列化后的节点对象
     */
    @SuppressWarnings("unchecked")
    protected <T extends WorkflowNode> T getNode(DelegateExecution execution, Class<T> expectedType) {
        FlowElement current = execution.getCurrentFlowElement();
        if (current == null) {
            throw new IllegalStateException("当前执行元素为空，无法获取节点定义");
        }
        Map<String, List<ExtensionElement>> extensions = current.getExtensionElements();
        if (extensions == null || !extensions.containsKey(NODE_DEFINITION_EXT)) {
            throw new IllegalStateException(
                    "BPMN 元素缺少 nodeDefinition 扩展: " + current.getId());
        }
        List<ExtensionElement> elements = extensions.get(NODE_DEFINITION_EXT);
        if (elements == null || elements.isEmpty()) {
            throw new IllegalStateException(
                    "nodeDefinition 扩展为空: " + current.getId());
        }
        String json = elements.get(0).getElementText();
        try {
            WorkflowNode node = JsonUtils.OBJECT_MAPPER.readValue(json, WorkflowNode.class);
            if (!expectedType.isInstance(node)) {
                throw new IllegalStateException(
                        "节点类型不匹配: 期望 " + expectedType.getSimpleName() +
                                ", 实际 " + node.getClass().getSimpleName() +
                                " (element=" + current.getId() + ")");
            }
            return (T) node;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "反序列化节点定义失败: " + current.getId(), e);
        }
    }

    protected WorkflowDefinition getWorkflowDefinition(DelegateExecution execution) {
        Object raw = execution.getVariable(VAR_WORKFLOW_DEFINITION);
        if (raw == null) {
            return null;
        }
        try {
            return JsonUtils.OBJECT_MAPPER.readValue(raw.toString(), WorkflowDefinition.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化工作流定义失败", e);
        }
    }
}
