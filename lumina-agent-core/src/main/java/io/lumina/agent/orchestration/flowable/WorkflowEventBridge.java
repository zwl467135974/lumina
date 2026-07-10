package io.lumina.agent.orchestration.flowable;

import io.lumina.agent.orchestration.engine.WorkflowEventListener;
import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowNodeStatus;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flowable 执行事件 → Lumina {@link WorkflowEventListener} 桥接器
 *
 * <p>作为 {@code delegateExpression="${workflowEventBridge}"} 注册到 BPMN 每个节点的
 * ExecutionListener（start / end 事件）。引擎在 execute/resume 前通过 ThreadLocal
 * 注入当前上下文和监听器列表，桥接器在 Flowable 回调时转发事件。
 *
 * <p>仅对携带 {@code lumina:nodeDefinition} 扩展元素的 BPMN 元素触发回调，
 * 网关、连线等结构性元素自动过滤。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component("workflowEventBridge")
public class WorkflowEventBridge implements ExecutionListener {

    static final ThreadLocal<WorkflowContext> CTX_HOLDER = new ThreadLocal<>();
    static final ThreadLocal<List<WorkflowEventListener>> LISTENERS_HOLDER = new ThreadLocal<>();
    static final ThreadLocal<Map<String, Long>> START_TIMES = new ThreadLocal<>();

    private static final String NODE_RESULT_PREFIX = "__nodeResult_";

    @Override
    public void notify(DelegateExecution execution) {
        WorkflowContext ctx = CTX_HOLDER.get();
        List<WorkflowEventListener> listenerList = LISTENERS_HOLDER.get();
        if (ctx == null || listenerList == null || listenerList.isEmpty()) {
            return;
        }

        FlowElement element = execution.getCurrentFlowElement();
        if (element == null) {
            return;
        }

        Map<String, List<ExtensionElement>> extensions = element.getExtensionElements();
        if (extensions == null || !extensions.containsKey(FlowableBpmnConverter.NODE_DEFINITION_EXT)) {
            return;
        }

        String nodeId = element.getId();
        String nodeName = element.getName();
        String eventName = execution.getEventName();

        if (ExecutionListener.EVENTNAME_START.equals(eventName)) {
            ensureStartTimes();
            START_TIMES.get().put(nodeId, System.currentTimeMillis());
            ctx.getNodeStatuses().put(nodeId, WorkflowNodeStatus.RUNNING);
            ctx.setCurrentNodeId(nodeId);
            listenerList.forEach(l -> l.onNodeStarted(nodeId, nodeName, ctx));

        } else if (ExecutionListener.EVENTNAME_END.equals(eventName)) {
            Map<String, Long> startTimes = START_TIMES.get();
            Long start = startTimes != null ? startTimes.remove(nodeId) : null;
            long duration = start != null ? System.currentTimeMillis() - start : 0;
            Object result = execution.getVariable(NODE_RESULT_PREFIX + nodeId);
            ctx.getNodeStatuses().put(nodeId, WorkflowNodeStatus.COMPLETED);
            ctx.setNodeResult(nodeId, result);
            listenerList.forEach(l -> l.onNodeCompleted(nodeId, result, duration));
        }
    }

    private void ensureStartTimes() {
        if (START_TIMES.get() == null) {
            START_TIMES.set(new HashMap<>());
        }
    }
}
