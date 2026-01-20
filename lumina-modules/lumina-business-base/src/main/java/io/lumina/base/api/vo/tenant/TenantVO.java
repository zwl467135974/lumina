package io.lumina.base.api.vo.tenant;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class TenantVO {

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 租户编码
     */
    private String tenantCode;

    /**
     * 租户名称
     */
    private String tenantName;

    /**
     * 联系人
     */
    private String contactName;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 联系邮箱
     */
    private String contactEmail;

    /**
     * 状态（0-禁用，1-启用）
     */
    private Integer status;

    /**
     * 过期时间（NULL表示永久）
     */
    private LocalDateTime expireTime;

    /**
     * 用户数量
     */
    private Integer userCount;

    /**
     * 角色数量
     */
    private Integer roleCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
