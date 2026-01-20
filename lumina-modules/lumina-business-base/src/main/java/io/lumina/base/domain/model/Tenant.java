package io.lumina.base.domain.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户领域实体
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class Tenant implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 租户编码（唯一标识）
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
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 当前用户数量（不持久化）
     */
    private Integer currentUserCount;

    /**
     * 激活租户
     */
    public void activate() {
        if (this.status == 1) {
            throw new IllegalStateException("租户已是启用状态");
        }
        this.status = 1;
    }

    /**
     * 禁用租户
     */
    public void deactivate() {
        if (this.status == 0) {
            throw new IllegalStateException("租户已是禁用状态");
        }
        this.status = 0;
    }

    /**
     * 判断是否启用
     */
    public boolean isActive() {
        return this.status == 1;
    }

    /**
     * 判断是否是系统租户
     */
    public boolean isSystemTenant() {
        return this.tenantId != null && this.tenantId == 0;
    }

    /**
     * 判断是否已过期
     */
    public boolean isExpired() {
        if (expireTime == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 验证租户编码
     */
    public void validateTenantCode() {
        if (tenantCode == null || tenantCode.trim().isEmpty()) {
            throw new IllegalArgumentException("租户编码不能为空");
        }
        String codeRegex = "^[A-Z0-9_]+$";
        if (!tenantCode.matches(codeRegex)) {
            throw new IllegalArgumentException("租户编码只能包含大写字母、数字和下划线");
        }
    }

    /**
     * 验证邮箱
     */
    public void validateEmail() {
        if (contactEmail != null && !contactEmail.isEmpty()) {
            String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
            if (!contactEmail.matches(emailRegex)) {
                throw new IllegalArgumentException("联系邮箱格式不正确");
            }
        }
    }

    /**
     * 验证手机号
     */
    public void validatePhone() {
        if (contactPhone != null && !contactPhone.isEmpty()) {
            String phoneRegex = "^1[3-9]\\d{9}$";
            if (!contactPhone.matches(phoneRegex)) {
                throw new IllegalArgumentException("联系电话格式不正确");
            }
        }
    }
}
