package io.lumina.agent.orchestration.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流定义
 *
 * <p>一个工作流的完整描述，包含节点列表、边列表、输入/输出定义。
 * 可从 YAML/JSON 反序列化，也可从数据库加载。
 *
 * <p>YAML 示例：
 * <pre>{@code
 * name: "customer-complaint-handler"
 * description: "客户投诉处理流程"
 * inputs:
 *   - name: complaint
 *     type: string
 *     required: true
 * nodes:
 *   - id: classify
 *     type: agent
 *     agentId: 1
 *     input: "#complaint"
 *     outputVar: "category"
 *   - id: route
 *     type: condition
 *     expression: "#category == 'refund'"
 *     trueBranch: refund-agent
 *     falseBranch: general-agent
 * edges:
 *   - from: classify
 *     to: route
 * outputs:
 *   result: "$.refund_result ?: $.general_result"
 * }</pre>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class WorkflowDefinition {

    /** 工作流名称（唯一标识） */
    private String name;

    /** 工作流描述 */
    private String description;

    /** 版本号 */
    private int version = 1;

    /** 输入参数定义 */
    private List<ParameterDef> inputs = new ArrayList<>();

    /** 节点列表 */
    private List<WorkflowNode> nodes = new ArrayList<>();

    /** 边列表（定义节点间连接） */
    private List<WorkflowEdge> edges = new ArrayList<>();

    /** 输出映射（变量名 → 表达式） */
    private MapEntry[] outputs;

    @JsonIgnore
    private transient volatile Map<String, List<WorkflowEdge>> outgoingEdgeIndex;

    @JsonIgnore
    private transient volatile Map<String, WorkflowNode> nodeIndex;

    @JsonIgnore
    private transient volatile Set<String> nodesWithIncomingEdges;

    private void ensureIndex() {
        if (outgoingEdgeIndex == null) {
            synchronized (this) {
                if (outgoingEdgeIndex == null) {
                    Map<String, List<WorkflowEdge>> index = new HashMap<>();
                    Map<String, WorkflowNode> nIdx = new HashMap<>();
                    Set<String> incoming = new HashSet<>();
                    for (WorkflowEdge edge : edges) {
                        index.computeIfAbsent(edge.getFrom(), k -> new ArrayList<>()).add(edge);
                        incoming.add(edge.getTo());
                    }
                    for (WorkflowNode node : nodes) {
                        nIdx.put(node.getId(), node);
                    }
                    for (Map.Entry<String, List<WorkflowEdge>> e : index.entrySet()) {
                        e.setValue(Collections.unmodifiableList(e.getValue()));
                    }
                    outgoingEdgeIndex = Collections.unmodifiableMap(index);
                    nodeIndex = Collections.unmodifiableMap(nIdx);
                    nodesWithIncomingEdges = Collections.unmodifiableSet(incoming);
                }
            }
        }
    }

    /**
     * 根据 ID 查找节点
     */
    @JsonIgnore
    public WorkflowNode findNode(String nodeId) {
        ensureIndex();
        return nodeIndex.get(nodeId);
    }

    /**
     * 获取起始节点（没有入边的节点）
     */
    @JsonIgnore
    public List<WorkflowNode> getStartNodes() {
        ensureIndex();
        return nodes.stream()
                .filter(n -> !nodesWithIncomingEdges.contains(n.getId()))
                .toList();
    }

    /**
     * 获取指定节点的后续边
     */
    @JsonIgnore
    public List<WorkflowEdge> getOutgoingEdges(String nodeId) {
        ensureIndex();
        return outgoingEdgeIndex.getOrDefault(nodeId, Collections.emptyList());
    }

    /**
     * 获取指定节点的后续节点（无条件边）
     */
    @JsonIgnore
    public List<WorkflowEdge> getDefaultOutgoing(String nodeId) {
        return getOutgoingEdges(nodeId).stream()
                .filter(e -> e.getCondition() == null || e.getCondition().isBlank())
                .toList();
    }

    /**
     * 输入参数定义
     */
    @Data
    public static class ParameterDef {
        private String name;
        private String type;
        private boolean required;
        private String defaultValue;
    }

    /**
     * 输出映射条目（简化 Map 序列化）
     */
    @Data
    public static class MapEntry {
        private String key;
        private String value;

        public MapEntry() {
        }

        public MapEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
