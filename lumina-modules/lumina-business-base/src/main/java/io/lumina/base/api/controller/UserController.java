package io.lumina.base.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.api.dto.user.AssignRoleDTO;
import io.lumina.base.api.dto.user.CreateUserDTO;
import io.lumina.base.api.dto.user.ResetPasswordDTO;
import io.lumina.base.api.dto.user.UpdateUserDTO;
import io.lumina.base.api.dto.user.UserQueryDTO;
import io.lumina.base.api.vo.user.UserVO;
import io.lumina.base.annotation.RequirePermission;
import io.lumina.base.service.UserService;
import io.lumina.common.core.R;
import io.lumina.common.core.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 用户控制器
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/base/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 创建用户
     */
    @PostMapping
    @RequirePermission("system:user:create")
    public R<Long> createUser(@Valid @RequestBody CreateUserDTO dto) {
        log.info("创建用户请求: username={}", dto.getUsername());
        Long userId = userService.createUser(dto);
        return R.success(userId);
    }

    /**
     * 更新用户
     */
    @PutMapping("/{userId}")
    @RequirePermission("system:user:update")
    public R<Boolean> updateUser(@PathVariable Long userId, @Valid @RequestBody UpdateUserDTO dto) {
        log.info("更新用户请求: userId={}", userId);
        dto.setUserId(userId);
        Boolean result = userService.updateUser(dto);
        return R.success(result);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{userId}")
    @RequirePermission("system:user:delete")
    public R<Boolean> deleteUser(@PathVariable Long userId) {
        log.info("删除用户请求: userId={}", userId);
        Boolean result = userService.deleteUser(userId);
        return R.success(result);
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{userId}")
    @RequirePermission("system:user:list")
    public R<UserVO> getUserById(@PathVariable Long userId) {
        log.info("查询用户详情: userId={}", userId);
        UserVO userVO = userService.getUserById(userId);
        return R.success(userVO);
    }

    /**
     * 根据用户名获取用户
     */
    @GetMapping("/username/{username}")
    public R<UserVO> getUserByUsername(@PathVariable String username) {
        log.info("根据用户名查询用户: username={}", username);
        UserVO userVO = userService.getUserByUsername(username);
        return R.success(userVO);
    }

    /**
     * 分页查询用户
     */
    @GetMapping
    @RequirePermission("system:user:list")
    public R<Page<UserVO>> listUsers(@Valid UserQueryDTO dto) {
        log.info("分页查询用户: current={}, size={}", dto.getCurrent(), dto.getSize());
        Page<UserVO> page = userService.listUsers(dto);
        return R.success(page);
    }

    /**
     * 分配角色
     */
    @PostMapping("/{userId}/roles")
    @RequirePermission("system:user:assign")
    public R<Boolean> assignRoles(@PathVariable Long userId, @Valid @RequestBody AssignRoleDTO dto) {
        log.info("分配角色请求: userId={}", userId);
        dto.setUserId(userId);
        Boolean result = userService.assignRoles(dto);
        return R.success(result);
    }

    /**
     * 重置密码
     */
    @PutMapping("/{userId}/password")
    @RequirePermission("system:user:reset")
    public R<Boolean> resetPassword(@PathVariable Long userId, @Valid @RequestBody ResetPasswordDTO dto) {
        log.info("重置密码请求: userId={}", userId);
        dto.setUserId(userId);
        Boolean result = userService.resetPassword(dto);
        return R.success(result);
    }
}
