package io.lumina.agent.orchestration.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 * 工作流节点基类
 *
 * <p>所有具体节点类型（Agent / Condition / Loop / Parallel / Transform / Human）继承此类。
 * 通过 Jackson 多态反序列化，YAML/JSON 中的 {@code type} 字段决定实例化哪个子类。
 *
 * <p>每个节点有以下公共属性：
 * <ul>
 *   <li>{@code id} — 工作流内唯一标识</li>
 *   <li>{@code name} — 显示名称</li>
 *   <li>{@code input} — 输入表达式（SpEL，从上下文变量中取值）</li>
 *   <li>{@code outputVar} — 输出变量名（执行结果存入上下文的哪个变量）</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AgentNode.class, name = "agent"),
        @JsonSubTypes.Type(value = ConditionNode.class, name = "condition"),
        @JsonSubTypes.Type(value = LoopNode.class, name = "loop"),
        @JsonSubTypes.Type(value = ParallelNode.class, name = "parallel"),
        @JsonSubTypes.Type(value = TransformNode.class, name = "transform"),
        @JsonSubTypes.Type(value = HumanNode.class, name = "human"),
        @JsonSubTypes.Type(value = AutonomyNode.class, name = "autonomy"),
})
public abstract class WorkflowNode {

    /** 节点 ID（工作流内唯一） */
    private String id;

    /** 节点名称 */
    private String name;

    /** 输入表达式（SpEL，如 {@code #complaint} 或 {@code $.input.text}） */
    private String input;

    /** 输出变量名（执行结果存入上下文变量的键名） */
    private String outputVar;
}
