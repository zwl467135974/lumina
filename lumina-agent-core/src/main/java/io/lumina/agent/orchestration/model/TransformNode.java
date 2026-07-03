package io.lumina.agent.orchestration.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据转换节点
 *
 * <p>不调用 LLM，仅通过 SpEL 表达式对上下文变量进行变换、提取、格式化。
 * 用于在工作流中做数据桥接，如提取 Agent 回复中的 JSON 字段、拼接字符串等。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TransformNode extends WorkflowNode {

    /** SpEL 变换表达式（如 {@code #result.toUpperCase()} 或 {@code T(JSON).parse(#result).get('name')}） */
    private String transformExpr;

    /** 静态模板（支持 {@code ${variable}} 占位符替换） */
    private String template;
}
