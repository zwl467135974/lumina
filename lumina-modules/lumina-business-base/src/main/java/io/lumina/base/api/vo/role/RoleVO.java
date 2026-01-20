package io.lumina.base.api.vo.role;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class RoleVO {

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 状态（0-禁用，1-启用）
     */
    private Integer status;

    /**
     * 权限数量
     */
    private Integer permissionCount;

    /**
     * 用户数量
     */
    private Integer userCount;

    /**
     * 权限列表
     */
    private List<PermissionVO> permissions;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 权限VO（内部类）
     */
    @Data
    public static class PermissionVO {
        /**
         * 权限ID
         */
        private Long permissionId;

        /**
         * 权限编码
         */
        private String permissionCode;

        /**
         * 权限名称
         */
        private String permissionName;
    }
}
