package io.lumina.agent.orchestration.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 人工审批节点（Human-in-the-Loop）
 *
 * <p>工作流执行到此节点时暂停，等待人工输入（审批/修改/确认）后继续。
 * 人工输入通过回调接口注入上下文变量。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HumanNode extends WorkflowNode {

    /** 审批提示信息 */
    private String prompt;

    /** 审批选项（可选，限定人工输入范围） */
    private String[] options;

    /** 默认审批结果（超时未审批时使用） */
    private String defaultDecision;

    /** 超时时间（秒，0=不超时） */
    private long timeoutSeconds;

    /** 审批结果存入的变量名 */
    private String decisionVar = "decision";
}
