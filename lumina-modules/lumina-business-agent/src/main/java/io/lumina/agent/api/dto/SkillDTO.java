package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新技能 DTO
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Data
public class SkillDTO {

    @NotBlank(message = "技能名称不能为空")
    @Size(max = 64, message = "技能名称最长 64 字符")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "技能名称必须是 kebab-case（小写字母/数字/连字符，如 refund-policy）")
    private String name;

    @NotBlank(message = "技能描述不能为空")
    @Size(max = 500, message = "技能描述最长 500 字符")
    private String description;

    @Size(max = 500, message = "适用场景说明最长 500 字符")
    private String whenToUse;

    @NotBlank(message = "技能内容不能为空")
    private String content;

    /** 是否启用（默认 true） */
    private Boolean enabled;
}
