package io.lumina.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.api.dto.user.AssignRoleDTO;
import io.lumina.base.api.dto.user.CreateUserDTO;
import io.lumina.base.api.dto.user.ResetPasswordDTO;
import io.lumina.base.api.dto.user.UpdateUserDTO;
import io.lumina.base.api.dto.user.UserQueryDTO;
import io.lumina.base.api.vo.user.UserVO;

/**
 * 用户服务
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public interface UserService {

    /**
     * 创建用户
     *
     * @param dto 创建用户 DTO
     * @return 用户ID
     */
    Long createUser(CreateUserDTO dto);

    /**
     * 更新用户
     *
     * @param dto 更新用户 DTO
     * @return 是否成功
     */
    Boolean updateUser(UpdateUserDTO dto);

    /**
     * 删除用户（逻辑删除）
     *
     * @param userId 用户ID
     * @return 是否成功
     */
    Boolean deleteUser(Long userId);

    /**
     * 获取用户详情
     *
     * @param userId 用户ID
     * @return 用户 VO
     */
    UserVO getUserById(Long userId);

    /**
     * 根据用户名获取用户（带租户隔离）
     *
     * @param username 用户名
     * @return 用户 VO
     */
    UserVO getUserByUsername(String username);

    /**
     * 分页查询用户
     *
     * @param dto 查询条件
     * @return 分页结果
     */
    Page<UserVO> listUsers(UserQueryDTO dto);

    /**
     * 分配角色
     *
     * @param dto 分配角色 DTO
     * @return 是否成功
     */
    Boolean assignRoles(AssignRoleDTO dto);

    /**
     * 重置密码
     *
     * @param dto 重置密码 DTO
     * @return 是否成功
     */
    Boolean resetPassword(ResetPasswordDTO dto);
}
