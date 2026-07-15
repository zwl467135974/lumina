package io.lumina.agent.orchestration.flowable;

import io.lumina.agent.orchestration.model.AgentNode;
import io.lumina.agent.orchestration.model.ConditionNode;
import io.lumina.agent.orchestration.model.HumanNode;
import io.lumina.agent.orchestration.model.LoopNode;
import io.lumina.agent.orchestration.model.ParallelNode;
import io.lumina.agent.orchestration.model.TransformNode;
import io.lumina.agent.orchestration.model.WorkflowDefinition;
import io.lumina.agent.orchestration.model.WorkflowEdge;
import io.lumina.agent.orchestration.model.WorkflowNode;
import io.lumina.agent.util.JsonUtils;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * YAML 工作流定义 → Flowable BPMN 模型转换器
 *
 * <p>将 {@link WorkflowDefinition}（YAML/JSON 反序列化的 DAG 模型）转换为
 * Flowable {@link BpmnModel}，用于部署到 Flowable 引擎执行。
 *
 * <h3>映射规则</h3>
 * <table border="1">
 * <tr><th>YAML 节点类型</th><th>BPMN 元素</th></tr>
 * <tr><td>start（自动生成）</td><td>StartEvent</td></tr>
 * <tr><td>end（自动生成）</td><td>EndEvent</td></tr>
 * <tr><td>agent</td><td>ServiceTask (delegateExpression=agentDelegate)</td></tr>
 * <tr><td>transform</td><td>ServiceTask (delegateExpression=transformDelegate)</td></tr>
 * <tr><td>condition (二分支)</td><td>ExclusiveGateway + 条件流 + 默认流</td></tr>
 * <tr><td>condition (多分支)</td><td>ExclusiveGateway + 多条件流 + 默认流</td></tr>
 * <tr><td>loop (集合遍历)</td><td>ServiceTask + multiInstanceLoopCharacteristics</td></tr>
 * <tr><td>loop (条件循环)</td><td>ServiceTask (loopDelegate 内部 while 循环)</td></tr>
 * <tr><td>parallel</td><td>ParallelGateway (fork) + ParallelGateway (join)</td></tr>
 * <tr><td>human</td><td>UserTask</td></tr>
 * </table>
 *
 * <h3>条件表达式转换</h3>
 * <p>YAML 使用 SpEL（{@code #var == 'value'}），Flowable 使用 JUEL（{@code ${var == 'value'}}）。
 * 转换器将 {@code #var} 引用替换为 {@code var}，并包裹在 {@code ${}} 中。
 *
 * <h3>节点定义传递</h3>
 * <p>每个 BPMN 元素携带一个 {@code lumina:nodeDefinition} 扩展元素，
 * 内含原始 YAML 节点的 JSON 序列化。Delegate 执行时反序列化获取完整属性。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public class FlowableBpmnConverter {

    public static final String LUMINA_NS_PREFIX = "lumina";
    public static final String LUMINA_NS = "https://lumina.io/bpmn";
    public static final String NODE_DEFINITION_EXT = "nodeDefinition";

    static final String START_EVENT_ID = "_start";
    static final String END_EVENT_ID = "_end";

    /**
     * 将工作流定义转换为 Flowable BPMN 模型
     *
     * @param definition YAML 工作流定义
     * @return 可部署到 Flowable 的 BPMN 模型
     */
    public BpmnModel convert(WorkflowDefinition definition) {
        BpmnModel model = new BpmnModel();
        model.addNamespace(LUMINA_NS_PREFIX, LUMINA_NS);

        Process process = new Process();
        process.setId(sanitizeId(definition.getName()));
        process.setName(definition.getName());

        StartEvent startEvent = new StartEvent();
        startEvent.setId(START_EVENT_ID);
        process.addFlowElement(startEvent);

        EndEvent endEvent = new EndEvent();
        endEvent.setId(END_EVENT_ID);
        process.addFlowElement(endEvent);

        Map<String, WorkflowNode> nodeMap = new LinkedHashMap<>();
        for (WorkflowNode node : definition.getNodes()) {
            nodeMap.put(node.getId(), node);
            FlowElement element = convertNode(node);
            process.addFlowElement(element);
        }

        Set<String> connections = new HashSet<>();

        for (WorkflowNode startNode : definition.getStartNodes()) {
            addFlow(process, START_EVENT_ID, startNode.getId(), null, connections);
        }

        for (WorkflowEdge edge : definition.getEdges()) {
            WorkflowNode source = nodeMap.get(edge.getFrom());
            if (source instanceof ConditionNode
                    || source instanceof ParallelNode
                    || source instanceof LoopNode) {
                continue;
            }
            String condition = isNotBlank(edge.getCondition())
                    ? spelToFlowableCondition(edge.getCondition()) : null;
            addFlow(process, edge.getFrom(), edge.getTo(), condition, connections);
        }

        Map<String, String> parallelJoinMap = new HashMap<>();

        for (WorkflowNode node : definition.getNodes()) {
            if (node instanceof ConditionNode condNode) {
                addConditionFlows(process, condNode, connections);
            } else if (node instanceof ParallelNode parallelNode) {
                addParallelFork(process, parallelNode, definition, connections, parallelJoinMap);
            } else if (node instanceof LoopNode loopNode) {
                addLoopFlows(process, loopNode, connections);
            }
        }

        connectTerminalNodes(process, nodeMap, connections, parallelJoinMap);

        model.addProcess(process);
        return model;
    }

    private FlowElement convertNode(WorkflowNode node) {
        if (node instanceof AgentNode) {
            return createDelegateTask(node, "agentDelegate");
        }
        if (node instanceof TransformNode) {
            return createDelegateTask(node, "transformDelegate");
        }
        if (node instanceof ConditionNode) {
            ExclusiveGateway gateway = new ExclusiveGateway();
            gateway.setId(node.getId());
            gateway.setName(node.getName());
            addNodeDefinition(gateway, node);
            return gateway;
        }
        if (node instanceof ParallelNode) {
            ParallelGateway gateway = new ParallelGateway();
            gateway.setId(node.getId());
            gateway.setName(node.getName());
            addNodeDefinition(gateway, node);
            return gateway;
        }
        if (node instanceof HumanNode humanNode) {
            UserTask userTask = new UserTask();
            userTask.setId(node.getId());
            userTask.setName(node.getName());
            if (isNotBlank(humanNode.getDecisionVar())) {
                userTask.setDocumentation("decisionVar: " + humanNode.getDecisionVar());
            }
            addNodeDefinition(userTask, node);
            return userTask;
        }
        if (node instanceof LoopNode loopNode) {
            return convertLoopNode(loopNode);
        }
        return createDelegateTask(node, "transformDelegate");
    }

    private ServiceTask createDelegateTask(WorkflowNode node, String delegateBeanName) {
        ServiceTask task = new ServiceTask();
        task.setId(node.getId());
        task.setName(node.getName());
        task.setImplementation("${" + delegateBeanName + "}");
        task.setImplementationType("delegateExpression");
        addNodeDefinition(task, node);
        return task;
    }

    private ServiceTask convertLoopNode(LoopNode loopNode) {
        ServiceTask task = new ServiceTask();
        task.setId(loopNode.getId());
        task.setName(loopNode.getName());
        task.setImplementation("${loopDelegate}");
        task.setImplementationType("delegateExpression");
        addNodeDefinition(task, loopNode);

        if (isNotBlank(loopNode.getIterateVar())) {
            MultiInstanceLoopCharacteristics mi = new MultiInstanceLoopCharacteristics();
            mi.setSequential(false);
            mi.setInputDataItem(spelToFlowableExpr(loopNode.getIterateVar()));
            mi.setElementVariable(loopNode.getItemVar());
            mi.setElementIndexVariable("_loopIndex");
            task.setLoopCharacteristics(mi);
        }

        return task;
    }

    private void addConditionFlows(Process process, ConditionNode condNode, Set<String> connections) {
        String gatewayId = condNode.getId();

        if (condNode.getBranches() != null && !condNode.getBranches().isEmpty()) {
            List<ConditionNode.Branch> branches = condNode.getBranches();
            boolean hasDefaultFlow = false;
            for (int i = 0; i < branches.size(); i++) {
                ConditionNode.Branch branch = branches.get(i);
                boolean isLast = (i == branches.size() - 1);
                if (isLast && !isNotBlank(branch.getCondition())) {
                    addFlow(process, gatewayId, branch.getTo(), null, connections);
                    hasDefaultFlow = true;
                } else {
                    addFlow(process, gatewayId, branch.getTo(),
                            spelToFlowableCondition(branch.getCondition()), connections);
                }
            }
            if (!hasDefaultFlow) {
                addFlow(process, gatewayId, END_EVENT_ID, null, connections);
            }
        } else {
            if (isNotBlank(condNode.getTrueBranch())) {
                addFlow(process, gatewayId, condNode.getTrueBranch(),
                        spelToFlowableCondition(condNode.getExpression()), connections);
            }
            if (isNotBlank(condNode.getFalseBranch())) {
                addFlow(process, gatewayId, condNode.getFalseBranch(), null, connections);
            }
        }
    }

    private void addParallelFork(Process process, ParallelNode parallelNode,
                                  WorkflowDefinition definition, Set<String> connections,
                                  Map<String, String> parallelJoinMap) {
        String forkId = parallelNode.getId();

        Set<String> branchStarts = new LinkedHashSet<>();
        for (ParallelNode.ParallelBranch branch : parallelNode.getBranches()) {
            String target = isNotBlank(branch.getStartNode())
                    ? branch.getStartNode() : branch.getName();
            if (target != null) {
                addFlow(process, forkId, target, null, connections);
                branchStarts.add(target);
            }
        }

        if (branchStarts.size() < 2) return;

        Map<String, Set<String>> branchReachability = new HashMap<>();
        for (String start : branchStarts) {
            branchReachability.put(start, traceReachable(definition, start));
        }

        Set<String> allReachable = new HashSet<>();
        for (Set<String> r : branchReachability.values()) allReachable.addAll(r);

        Set<String> branchExclusive = new HashSet<>();
        Set<String> convergenceNodes = new LinkedHashSet<>();
        for (String node : allReachable) {
            int count = 0;
            for (Set<String> r : branchReachability.values()) {
                if (r.contains(node)) count++;
            }
            if (count >= 2) convergenceNodes.add(node);
            else branchExclusive.add(node);
        }
        convergenceNodes.removeAll(branchStarts);

        String joinId = forkId + "_join";
        boolean joinCreated = false;

        for (String convergenceTarget : convergenceNodes) {
            List<String> sources = new ArrayList<>();
            for (FlowElement element : new ArrayList<>(process.getFlowElements())) {
                if (!(element instanceof SequenceFlow sf)) continue;
                if (sf.getTargetRef().equals(convergenceTarget)
                        && branchExclusive.contains(sf.getSourceRef())) {
                    sources.add(sf.getSourceRef());
                }
            }
            if (sources.size() < 2) continue;

            if (!joinCreated) {
                ParallelGateway joinGateway = new ParallelGateway();
                joinGateway.setId(joinId);
                joinGateway.setName(parallelNode.getName() + " Join");
                process.addFlowElement(joinGateway);
                joinCreated = true;
            }

            for (String source : sources) {
                removeSequenceFlow(process, source, convergenceTarget);
                connections.remove(source + "->" + convergenceTarget);
                addFlow(process, source, joinId, null, connections);
            }
            addFlow(process, joinId, convergenceTarget, null, connections);
            break;
        }

        if (!joinCreated) {
            List<WorkflowEdge> outgoing = definition.getOutgoingEdges(forkId);
            if (!outgoing.isEmpty()) {
                ParallelGateway joinGateway = new ParallelGateway();
                joinGateway.setId(joinId);
                joinGateway.setName(parallelNode.getName() + " Join");
                process.addFlowElement(joinGateway);
                joinCreated = true;

                for (WorkflowEdge edge : outgoing) {
                    addFlow(process, joinId, edge.getTo(), null, connections);
                }

                for (String nodeId : branchExclusive) {
                    parallelJoinMap.put(nodeId, joinId);
                }
            }
        }
    }

    private Set<String> traceReachable(WorkflowDefinition definition, String startNode) {
        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startNode);
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            if (reachable.contains(nodeId)) continue;
            reachable.add(nodeId);
            for (WorkflowEdge edge : definition.getOutgoingEdges(nodeId)) {
                queue.add(edge.getTo());
            }
        }
        return reachable;
    }

    private void removeSequenceFlow(Process process, String source, String target) {
        process.getFlowElements().removeIf(e ->
                e instanceof SequenceFlow sf
                        && sf.getSourceRef().equals(source)
                        && sf.getTargetRef().equals(target));
    }

    private void addLoopFlows(Process process, LoopNode loopNode, Set<String> connections) {
        if (isNotBlank(loopNode.getExitTarget())) {
            addFlow(process, loopNode.getId(), loopNode.getExitTarget(), null, connections);
        }
    }

    private void connectTerminalNodes(Process process, Map<String, WorkflowNode> nodeMap,
                                       Set<String> connections, Map<String, String> parallelJoinMap) {
        Set<String> nodesWithOutgoing = new HashSet<>();
        for (FlowElement element : process.getFlowElements()) {
            if (element instanceof SequenceFlow sf) {
                nodesWithOutgoing.add(sf.getSourceRef());
            }
        }
        for (WorkflowNode node : nodeMap.values()) {
            if (!nodesWithOutgoing.contains(node.getId())) {
                String target = parallelJoinMap.getOrDefault(node.getId(), END_EVENT_ID);
                addFlow(process, node.getId(), target, null, connections);
            }
        }
    }

    private void addFlow(Process process, String source, String target,
                         String condition, Set<String> connections) {
        if (source == null || target == null) return;
        String key = source + "->" + target;
        if (connections.contains(key)) return;
        connections.add(key);

        SequenceFlow flow = new SequenceFlow();
        flow.setId("_flow_" + sanitizeId(source) + "_" + sanitizeId(target));
        flow.setSourceRef(source);
        flow.setTargetRef(target);
        if (condition != null) {
            flow.setConditionExpression(condition);
        }
        process.addFlowElement(flow);
    }

    private void addNodeDefinition(FlowElement element, WorkflowNode node) {
        try {
            String json = JsonUtils.OBJECT_MAPPER.writeValueAsString(node);
            ExtensionElement ext = new ExtensionElement();
            ext.setName(NODE_DEFINITION_EXT);
            ext.setNamespacePrefix(LUMINA_NS_PREFIX);
            ext.setNamespace(LUMINA_NS);
            ext.setElementText(json);
            element.addExtensionElement(ext);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.WORKFLOW_PARSE_FAILED, "序列化节点定义失败: " + node.getId(), e);
        }
    }

    /**
     * SpEL 条件表达式 → Flowable JUEL 条件表达式
     *
     * <p>{@code #category == 'refund'} → {@code ${category == 'refund'}}
     */
    String spelToFlowableCondition(String spel) {
        if (spel == null || spel.isBlank()) return null;
        return "${" + convertSpelVars(spel.trim()) + "}";
    }

    /**
     * SpEL 变量引用 → Flowable 表达式
     *
     * <p>{@code #items} → {@code ${items}}
     */
    String spelToFlowableExpr(String spel) {
        if (spel == null || spel.isBlank()) return null;
        return "${" + convertSpelVars(spel.trim()) + "}";
    }

    /**
     * 将 SpEL 的 {@code #variable} 引用替换为 JUEL 的 {@code variable}
     */
    private String convertSpelVars(String expr) {
        return expr.replaceAll("#([a-zA-Z_][a-zA-Z0-9_.]*)", "$1");
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String sanitizeId(String raw) {
        if (raw == null || raw.isBlank()) return "process";
        return raw.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
