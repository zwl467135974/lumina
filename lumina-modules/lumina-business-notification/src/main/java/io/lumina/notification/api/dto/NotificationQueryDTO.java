package io.lumina.notification.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 通知查询 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class NotificationQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 通知分类（可选过滤）
     */
    private String category;

    /**
     * 是否已读：null-全部，0-未读，1-已读
     */
    private Integer isRead;

    /**
     * 页码
     */
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    /**
     * 每页条数
     */
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    private Integer pageSize = 20;
}
