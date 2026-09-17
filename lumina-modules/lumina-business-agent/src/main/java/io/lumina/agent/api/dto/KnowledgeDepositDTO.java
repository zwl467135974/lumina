package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识沉淀创建 DTO
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
public class KnowledgeDepositDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长 200 字符")
    private String title;

    @NotBlank(message = "沉淀内容不能为空")
    @Size(max = 200000, message = "沉淀内容最长 200000 字符")
    private String content;

    @NotNull(message = "目标知识库不能为空")
    private Long kbId;

    /** TASK / MANUAL */
    @Pattern(regexp = "TASK|MANUAL", message = "来源类型必须是 TASK 或 MANUAL")
    private String sourceType;

    @Size(max = 64, message = "来源标识最长 64 字符")
    private String sourceId;

    private Long agentId;
}
