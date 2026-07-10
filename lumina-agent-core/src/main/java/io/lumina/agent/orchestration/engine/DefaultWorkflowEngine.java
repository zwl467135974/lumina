package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.*;
import io.lumina.agent.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认工作流引擎实现
 *
 * <p>基于 DAG（有向无环图）拓扑遍历执行节点。支持：
 * <ul>
 *   <li>顺序执行（沿 edge 连接）</li>
 *   <li>条件路由（{@link ConditionNode} 返回路由指令）</li>
 *   <li>并行执行（{@link ParallelNode} 多分支 fan-out）</li>
 *   <li>循环执行（{@link LoopNode} 集合遍历 / 条件循环）</li>
 *   <li>人工暂停（{@link HumanNode} 抛异常暂停）</li>
 * </ul>
 *
 * <p>执行策略：
 * <ol>
 *   <li>找到起始节点（无入边）</li>
 *   <li>依次执行，节点结果存入 {@code outputVar} → 上下文变量</li>
 *   <li>条件节点返回 {@code "route:targetId"}，引擎跳转到指定节点</li>
 *   <li>其他节点沿默认边（无条件 edge）继续</li>
 * </ol>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class DefaultWorkflowEngine implements WorkflowEngine {

    private final List<NodeExecutor> executors;
    private final ExpressionEvaluator expressionEvaluator;
    private final List<WorkflowEventListener> listeners = new CopyOnWriteArrayList<>();

    @Autowired(required = false)
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public DefaultWorkflowEngine(List<NodeExecutor> executors, ExpressionEvaluator expressionEvaluator) {
        this.executors = executors;
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public WorkflowContext execute(WorkflowDefinition definition, Map<String, Object> inputs) {
        WorkflowContext ctx = new WorkflowContext();
        ctx.setWorkflowName(definition.getName());
        ctx.setStatus(WorkflowStatus.RUNNING);

        if (inputs != null) {
            ctx.getVariables().putAll(inputs);
        }

        List<WorkflowNode> startNodes = definition.getStartNodes();
        String startNodeId = startNodes.isEmpty() ? null : startNodes.get(0).getId();

        return doExecute(definition, ctx, startNodeId);
    }

    @Override
    public WorkflowContext resume(WorkflowDefinition definition, WorkflowContext pausedCtx, String decision) {
        String humanNodeId = pausedCtx.getCurrentNodeId();
        WorkflowNode node = definition.findNode(humanNodeId);
        if (!(node instanceof HumanNode humanNode)) {
            throw new IllegalStateException("当前节点不是人工审批节点: " + humanNodeId);
        }

        pausedCtx.setVariable(humanNode.getDecisionVar(), decision);
        pausedCtx.setStatus(WorkflowStatus.RUNNING);

        String nextNodeId = determineNextNode(definition, node, decision, pausedCtx);
        log.info("工作流恢复执行: 从节点 {} 继续", nextNodeId);

        return doExecute(definition, pausedCtx, nextNodeId);
    }

    private WorkflowContext doExecute(WorkflowDefinition definition, WorkflowContext ctx, String startNodeId) {
        long workflowStart = System.currentTimeMillis();
        try {
            if (startNodeId == null) {
                throw new IllegalStateException("工作流没有起始节点: " + definition.getName());
            }
            String currentNodeId = startNodeId;
            Set<String> visited = new HashSet<>();
            int maxSteps = definition.getNodes().size() * 10 + 100;

            while (currentNodeId != null) {
                if (visited.contains(currentNodeId)) {
                    log.warn("检测到循环，跳过已访问节点: {}", currentNodeId);
                    break;
                }
                if (maxSteps-- <= 0) {
                    log.error("工作流执行步数超限，强制终止");
                    break;
                }

                visited.add(currentNodeId);
                WorkflowNode node = definition.findNode(currentNodeId);
                if (node == null) {
                    log.error("节点不存在: {}", currentNodeId);
                    break;
                }

                currentNodeId = executeNode(definition, node, ctx);
            }

            evaluateOutputs(definition, ctx);
            ctx.setStatus(WorkflowStatus.COMPLETED);
            listeners.forEach(l -> l.onWorkflowCompleted(ctx));

        } catch (HumanNodeExecutor.HumanApprovalRequiredException e) {
            ctx.setStatus(WorkflowStatus.PAUSED);
            ctx.setVariable(e.getDecisionVar(), "__WAITING__");
            ctx.setCurrentNodeId(e.getNodeId());
            log.info("工作流暂停等待人工审批: node={}", e.getNodeId());

        } catch (Exception e) {
            ctx.setStatus(WorkflowStatus.FAILED);
            ctx.setErrorMessage(e.getMessage());
            listeners.forEach(l -> l.onWorkflowFailed(ctx, e.getMessage()));
            log.error("工作流执行失败: {}", definition.getName(), e);
        }

        recordWorkflowTimer(definition.getName(), ctx.getStatus().name(),
                System.currentTimeMillis() - workflowStart);
        return ctx;
    }

    private String executeNode(WorkflowDefinition definition, WorkflowNode node, WorkflowContext ctx) {
        ctx.setCurrentNodeId(node.getId());
        ctx.getNodeStatuses().put(node.getId(), WorkflowNodeStatus.RUNNING);
        listeners.forEach(l -> l.onNodeStarted(node.getId(), node.getName(), ctx));

        NodeExecutor executor = findExecutor(node);
        if (executor == null) {
            throw new IllegalStateException("找不到节点执行器: " + node.getClass().getSimpleName());
        }

        long start = System.currentTimeMillis();
        try {
            Object result = executor.execute(node, ctx);
            long duration = System.currentTimeMillis() - start;

            if (node.getOutputVar() != null && !node.getOutputVar().isBlank()) {
                ctx.setVariable(node.getOutputVar(), result);
            }
            ctx.setNodeResult(node.getId(), result);
            ctx.getNodeStatuses().put(node.getId(), WorkflowNodeStatus.COMPLETED);
            listeners.forEach(l -> l.onNodeCompleted(node.getId(), result, duration));
            recordNodeTimer(node.getClass().getSimpleName(), "success", duration);

            if (result instanceof ParallelNodeExecutor.ParallelSignal signal) {
                return executeParallelBranches(definition, node, signal, ctx);
            }

            if (result instanceof LoopNodeExecutor.LoopSignal signal) {
                return executeLoop(definition, node, signal, ctx);
            }

            return determineNextNode(definition, node, result, ctx);

        } catch (HumanNodeExecutor.HumanApprovalRequiredException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            ctx.getNodeStatuses().put(node.getId(), WorkflowNodeStatus.FAILED);
            listeners.forEach(l -> l.onNodeFailed(node.getId(), e));
            recordNodeTimer(node.getClass().getSimpleName(), "failure", duration);
            log.error("节点执行失败: id={}, durationMs={}", node.getId(), duration, e);
            throw new RuntimeException("节点执行失败: " + node.getId(), e);
        }
    }

    private String determineNextNode(WorkflowDefinition definition, WorkflowNode node, Object result, WorkflowContext ctx) {
        if (result instanceof String str && str.startsWith(ConditionNodeExecutor.ROUTE_PREFIX)) {
            return str.substring(ConditionNodeExecutor.ROUTE_PREFIX.length());
        }

        List<WorkflowEdge> outgoing = definition.getOutgoingEdges(node.getId());
        for (WorkflowEdge edge : outgoing) {
            if (edge.getCondition() == null || edge.getCondition().isBlank()) {
                return edge.getTo();
            }
            if (expressionEvaluator.evaluateBoolean(edge.getCondition(), ctx.toEvaluationRoot())) {
                return edge.getTo();
            }
        }

        return null;
    }

    /**
     * 执行并行分支（Virtual Thread fan-out / fan-in）
     *
     * <p>每个分支获得一份上下文快照（深拷贝 variables / nodeResults / nodeStatuses），
     * 独立执行子链，完成后将结果按分支名合并到主上下文。
     *
     * @return 并行节点之后的下一个节点 ID（由 outgoing edge 决定），或 null 表示工作流结束
     */
    private String executeParallelBranches(WorkflowDefinition definition,
                                            WorkflowNode node,
                                            ParallelNodeExecutor.ParallelSignal signal,
                                            WorkflowContext ctx) {
        Set<String> originalVarKeys = new HashSet<>(ctx.getVariables().keySet());

        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = signal.branches().stream()
                    .map(branch -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            WorkflowContext branchCtx = copyContext(ctx);
                            log.info("并行分支启动: {}", branch.name());
                            executeChain(definition, branch.startNode(), branchCtx);
                            Object branchResult = branchCtx.getNodeResult(branch.startNode());
                            log.info("并行分支完成: {}, resultLen={}", branch.name(),
                                    branchResult != null ? branchResult.toString().length() : 0);
                            return Map.entry(branch, branchCtx);
                        } catch (Exception e) {
                            log.error("并行分支异常: {}", branch.name(), e);
                            return Map.entry(branch, copyContext(ctx));
                        }
                    }, executor))
                    .toList();

            if (signal.waitAll()) {
                java.util.concurrent.CompletableFuture.allOf(
                        futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            } else {
                java.util.concurrent.CompletableFuture.anyOf(
                        futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            }

            java.util.Map<String, Object> merged = new java.util.LinkedHashMap<>();
            for (var f : futures) {
                if (signal.waitAll() || f.isDone()) {
                    var entry = f.join();
                    ParallelNodeExecutor.ParallelBranchInfo branch = entry.getKey();
                    WorkflowContext branchCtx = entry.getValue();
                    Object branchResult = branchCtx.getNodeResult(branch.startNode());
                    merged.put(branch.name(), branchResult);

                    branchCtx.getVariables().entrySet().stream()
                            .filter(e -> !originalVarKeys.contains(e.getKey()))
                            .forEach(e -> ctx.setVariable(branch.name() + "_" + e.getKey(), e.getValue()));
                } else {
                    f.cancel(true);
                }
            }

            ctx.setNodeResult("__parallel_merged__", merged);
            for (var entry : merged.entrySet()) {
                ctx.setVariable(entry.getKey(), entry.getValue());
            }

            log.info("并行分支合并完成: branches={}", merged.size());
        }

        return determineNextNode(definition, node, null, ctx);
    }

    /**
     * 从指定节点开始执行子链（沿 edge 顺序遍历直到无后续节点）
     */
    private void executeChain(WorkflowDefinition definition, String startNodeId, WorkflowContext ctx) {
        String currentNodeId = startNodeId;
        Set<String> visited = new HashSet<>();

        while (currentNodeId != null) {
            if (visited.contains(currentNodeId)) {
                break;
            }
            visited.add(currentNodeId);

            WorkflowNode node = definition.findNode(currentNodeId);
            if (node == null) {
                break;
            }

            currentNodeId = executeNode(definition, node, ctx);
        }
    }

    /**
     * 深拷贝工作流上下文（复制 variables / nodeResults / nodeStatuses map）
     */
    private WorkflowContext copyContext(WorkflowContext source) {
        WorkflowContext copy = new WorkflowContext();
        copy.setInstanceId(source.getInstanceId());
        copy.setWorkflowName(source.getWorkflowName());
        copy.setTenantId(source.getTenantId());
        copy.setUserId(source.getUserId());
        copy.setVariables(JsonUtils.OBJECT_MAPPER.convertValue(
                source.getVariables(), new TypeReference<Map<String, Object>>() {}));
        copy.setNodeResults(new HashMap<>(source.getNodeResults()));
        copy.setNodeStatuses(new HashMap<>(source.getNodeStatuses()));
        copy.setCurrentNodeId(source.getCurrentNodeId());
        copy.setStatus(source.getStatus());
        copy.setErrorMessage(source.getErrorMessage());
        return copy;
    }

    /**
     * 执行循环节点
     *
     * <p>根据 {@link LoopNodeExecutor.LoopSignal} 的迭代次数，重复执行 loopTarget 子图，
     * 每轮设置当前元素（itemVar）和迭代索引（_loopIndex），完成后路由到 exitTarget。
     */
    @SuppressWarnings("unchecked")
    private String executeLoop(WorkflowDefinition definition, WorkflowNode loopNode,
                               LoopNodeExecutor.LoopSignal signal, WorkflowContext ctx) {
        List<Object> items = ctx.getVariable("__loopItems__");
        String itemVar = ctx.getVariable("__loopItemVar__");

        for (int i = 0; i < signal.iterations(); i++) {
            if (signal.conditionExpr() != null && !signal.conditionExpr().isBlank()) {
                if (!expressionEvaluator.evaluateBoolean(signal.conditionExpr(), ctx.toEvaluationRoot())) {
                    log.info("条件循环退出: iteration={}", i);
                    break;
                }
            }
            if (items != null && i < items.size() && itemVar != null) {
                ctx.setVariable(itemVar, items.get(i));
            }
            ctx.setVariable("_loopIndex", i);
            log.debug("循环迭代 {}/{}: loopTarget={}", i + 1, signal.iterations(), signal.loopTarget());
            executeChain(definition, signal.loopTarget(), ctx);
        }

        if (signal.exitTarget() != null && !signal.exitTarget().isBlank()) {
            return signal.exitTarget();
        }
        return determineNextNode(definition, loopNode, null, ctx);
    }

    private void evaluateOutputs(WorkflowDefinition definition, WorkflowContext ctx) {
        if (definition.getOutputs() == null) return;
        for (WorkflowDefinition.MapEntry entry : definition.getOutputs()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                Object value = expressionEvaluator.evaluate(entry.getValue(), ctx.toEvaluationRoot());
                ctx.setVariable(entry.getKey(), value);
            }
        }
    }

    private NodeExecutor findExecutor(WorkflowNode node) {
        return executors.stream()
                .filter(e -> e.supports(node))
                .findFirst()
                .orElse(null);
    }

    private void recordWorkflowTimer(String name, String result, long durationMs) {
        if (meterRegistry != null) {
            meterRegistry.timer("workflow.execution.duration", "name", name, "result", result)
                    .record(java.time.Duration.ofMillis(durationMs));
        }
    }

    private void recordNodeTimer(String nodeType, String result, long durationMs) {
        if (meterRegistry != null) {
            meterRegistry.timer("workflow.node.duration", "type", nodeType, "result", result)
                    .record(java.time.Duration.ofMillis(durationMs));
        }
    }

    @Override
    public void addListener(WorkflowEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(WorkflowEventListener listener) {
        listeners.remove(listener);
    }
}
