package io.lumina.base.api.dto.user;

import io.lumina.common.core.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户查询 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends BaseDTO {

    /**
     * 用户名（模糊查询）
     */
    private String username;

    /**
     * 真实姓名（模糊查询）
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
     * 状态（0-禁用，1-启用）
     */
    private Integer status;

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 当前页码
     */
    private Integer current = 1;

    /**
     * 每页大小
     */
    private Integer size = 10;

    /**
     * 排序字段
     */
    private String orderBy = "user_id";

    /**
     * 排序方向（asc/desc）
     */
    private String orderDirection = "desc";
}
