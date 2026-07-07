package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库请求 DTO（E5）
 *
 * @author Lumina Team
 * @since 2.1.0
 */
@Data
public class KnowledgeBaseDTO {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 200, message = "名称长度不能超过200")
    private String name;

    @Size(max = 500, message = "描述长度不能超过500")
    private String description;

    /** PRIVATE / TEAM / PUBLIC */
    private String visibility = "PRIVATE";
}
