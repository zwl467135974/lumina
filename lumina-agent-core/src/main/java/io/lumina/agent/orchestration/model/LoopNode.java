package io.lumina.agent.orchestration.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 循环节点
 *
 * <p>对集合迭代执行子工作流，或基于条件表达式重复执行。
 *
 * <p>两种模式：
 * <ul>
 *   <li>{@code iterateVar} 非空 — 遍历集合，每轮当前元素存入 {@code itemVar}</li>
 *   <li>{@code conditionExpr} 非空 — 条件为 true 时重复执行，直到条件为 false 或达到 {@code maxIterations}</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoopNode extends WorkflowNode {

    /** 遍历集合的表达式（如 {@code #items}） */
    private String iterateVar;

    /** 当前迭代元素的变量名（默认 {@code item}） */
    private String itemVar = "item";

    /** 循环条件的 SpEL 表达式（条件循环模式） */
    private String conditionExpr;

    /** 最大迭代次数（防止无限循环，默认 100） */
    private int maxIterations = 100;

    /** 循环体目标节点 ID（每轮执行的起始节点） */
    private String loopTarget;

    /** 循环结束后路由到的目标节点 ID */
    private String exitTarget;
}
