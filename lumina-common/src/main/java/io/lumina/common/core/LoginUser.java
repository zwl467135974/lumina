package io.lumina.common.core;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录用户信息
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 角色列表
     */
    private String[] roles;

    /**
     * 权限列表
     */
    private String[] permissions;

    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 过期时间
     */
    private Long expireTime;

    /**
     * Token
     */
    private String token;

    /**
     * 额外信息
     */
    private Map<String, Object> extra;

    public LoginUser() {
        this.extra = new HashMap<>();
    }

    /**
     * 判断是否过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }

    /**
     * 获取额外信息
     */
    public Object getExtra(String key) {
        return extra.get(key);
    }

    /**
     * 设置额外信息
     */
    public void setExtra(String key, Object value) {
        this.extra.put(key, value);
    }

    /**
     * 获取主角色（第一个角色）
     *
     * @return 主角色，如果不存在返回 null
     */
    public String getRole() {
        if (roles == null || roles.length == 0) {
            return null;
        }
        return roles[0];
    }

    /**
     * 设置主角色
     *
     * @param role 角色
     */
    public void setRole(String role) {
        this.roles = new String[]{role};
    }
}
