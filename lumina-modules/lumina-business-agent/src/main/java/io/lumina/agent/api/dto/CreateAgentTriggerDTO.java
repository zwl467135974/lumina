package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 Agent 定时触发器 DTO
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@Data
public class CreateAgentTriggerDTO {

    /**
     * 触发器名称
     */
    @NotBlank(message = "触发器名称不能为空")
    @Size(max = 64, message = "触发器名称不能超过 64 字符")
    private String name;

    /**
     * 目标 Agent ID
     */
    @NotNull(message = "Agent ID 不能为空")
    private Long agentId;

    /**
     * 目标工作流 ID（预留，首版仅支持 agentId）
     */
    private Long workflowId;

    /**
     * cron 表达式，Spring 6 字段格式：秒 分 时 日 月 周
     *
     * <p>如 {@code 0 0 9 * * *} = 每天 9 点（注意不是 Quartz 7 字段格式）
     */
    @NotBlank(message = "cron 表达式不能为空")
    @Size(max = 64, message = "cron 表达式不能超过 64 字符")
    private String cronExpr;

    /**
     * 触发时提交给 Agent 的任务输入
     */
    @NotBlank(message = "任务输入不能为空")
    private String inputText;

    /**
     * 错过策略：FIRE_ONCE=补触发一次（默认）/ SKIP=跳过
     */
    @Pattern(regexp = "FIRE_ONCE|SKIP", message = "misfirePolicy 只能是 FIRE_ONCE 或 SKIP")
    private String misfirePolicy;
}
