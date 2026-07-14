package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 多模态执行请求 DTO
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Data
public class MultimodalRequestDTO {

    /** 任务描述 */
    @NotBlank(message = "任务描述不能为空")
    @Size(max = 10000, message = "任务描述不能超过10000字符")
    private String task;

    /** 图片文件 UUID 列表 */
    @Size(max = 20, message = "单次最多上传20张图片")
    private List<String> fileUuids;

    /** 会话 UUID（可选） */
    private String conversationId;
}
