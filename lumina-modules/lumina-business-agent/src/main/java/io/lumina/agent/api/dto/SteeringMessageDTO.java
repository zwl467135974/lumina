package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 运行中转向消息 DTO（v3.14 批次 3.2）
 *
 * @author Lumina Team
 * @since 3.14.0
 */
@Data
public class SteeringMessageDTO {

    @NotBlank(message = "转向消息不能为空")
    @Size(max = 4000, message = "转向消息长度不能超过 4000 字符")
    private String message;
}
