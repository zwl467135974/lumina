package io.lumina.base.api.vo.user;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class UserVO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 租户名称
     */
    private String tenantName;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 状态（0-禁用，1-启用）
     */
    private Integer status;

    /**
     * 角色列表
     */
    private List<RoleVO> roles;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 角色VO（内部类）
     */
    @Data
    public static class RoleVO {
        /**
         * 角色ID
         */
        private Long roleId;

        /**
         * 角色编码
         */
        private String roleCode;

        /**
         * 角色名称
         */
        private String roleName;
    }
}
