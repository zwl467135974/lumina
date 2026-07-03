package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.model.HumanNode;
import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 人工审批节点执行器
 *
 * <p>抛出 {@link HumanApprovalRequiredException} 暂停工作流执行，
 * 等待人工通过回调接口注入审批结果后继续。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class HumanNodeExecutor implements NodeExecutor {

    @Override
    public boolean supports(WorkflowNode node) {
        return node instanceof HumanNode;
    }

    @Override
    public Object execute(WorkflowNode node, WorkflowContext ctx) {
        HumanNode humanNode = (HumanNode) node;
        log.info("人工审批节点暂停: id={}, prompt={}", node.getId(), humanNode.getPrompt());
        throw new HumanApprovalRequiredException(node.getId(), humanNode.getPrompt(), humanNode.getDecisionVar());
    }

    /**
     * 人工审批请求异常
     */
    public static class HumanApprovalRequiredException extends RuntimeException {

        private final String nodeId;
        private final String prompt;
        private final String decisionVar;

        public HumanApprovalRequiredException(String nodeId, String prompt, String decisionVar) {
            super("人工审批节点暂停: " + nodeId);
            this.nodeId = nodeId;
            this.prompt = prompt;
            this.decisionVar = decisionVar;
        }

        public String getNodeId() { return nodeId; }
        public String getPrompt() { return prompt; }
        public String getDecisionVar() { return decisionVar; }
    }
}
