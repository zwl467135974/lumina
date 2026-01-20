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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "用户管理", description = "用户账户管理、角色分配、密码重置等接口")
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/base/users")
@SecurityRequirement(name = "JWT Authentication")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 创建用户
     */
    @PostMapping
    @RequirePermission("system:user:create")
    @Operation(summary = "创建用户", description = "创建新的用户账户，支持分配初始角色")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功", content = @Content(schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "400", description = "请求参数错误", content = @Content),
        @ApiResponse(responseCode = "401", description = "未授权", content = @Content),
        @ApiResponse(responseCode = "403", description = "权限不足", content = @Content)
    })
    public R<Long> createUser(
            @Parameter(description = "用户创建信息", required = true)
            @Valid @RequestBody CreateUserDTO dto) {
        log.info("创建用户请求: username={}", dto.getUsername());
        Long userId = userService.createUser(dto);
        return R.success(userId);
    }

    /**
     * 更新用户
     */
    @PutMapping("/{userId}")
    @RequirePermission("system:user:update")
    @Operation(summary = "更新用户", description = "更新用户基本信息，不包括密码")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public R<Boolean> updateUser(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "用户更新信息", required = true)
            @Valid @RequestBody UpdateUserDTO dto) {
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
    @Operation(summary = "删除用户", description = "删除指定用户，此操作不可恢复")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public R<Boolean> deleteUser(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId) {
        log.info("删除用户请求: userId={}", userId);
        Boolean result = userService.deleteUser(userId);
        return R.success(result);
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{userId}")
    @RequirePermission("system:user:list")
    @Operation(summary = "获取用户详情", description = "根据用户ID获取用户详细信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public R<UserVO> getUserById(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId) {
        log.info("查询用户详情: userId={}", userId);
        UserVO userVO = userService.getUserById(userId);
        return R.success(userVO);
    }

    /**
     * 根据用户名获取用户
     */
    @GetMapping("/username/{username}")
    @Operation(summary = "根据用户名获取用户", description = "根据用户名查询用户信息，通常用于登录验证")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public R<UserVO> getUserByUsername(
            @Parameter(description = "用户名", required = true, example = "admin")
            @PathVariable String username) {
        log.info("根据用户名查询用户: username={}", username);
        UserVO userVO = userService.getUserByUsername(username);
        return R.success(userVO);
    }

    /**
     * 分页查询用户
     */
    @GetMapping
    @RequirePermission("system:user:list")
    @Operation(summary = "分页查询用户", description = "支持按用户名、昵称、邮箱等条件分页查询用户列表")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功")
    })
    public R<Page<UserVO>> listUsers(
            @Parameter(description = "查询条件")
            @Valid UserQueryDTO dto) {
        log.info("分页查询用户: current={}, size={}", dto.getCurrent(), dto.getSize());
        Page<UserVO> page = userService.listUsers(dto);
        return R.success(page);
    }

    /**
     * 分配角色
     */
    @PostMapping("/{userId}/roles")
    @RequirePermission("system:user:assign")
    @Operation(summary = "分配用户角色", description = "为用户分配一个或多个角色，会覆盖用户原有的所有角色")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "分配成功"),
        @ApiResponse(responseCode = "404", description = "用户或角色不存在")
    })
    public R<Boolean> assignRoles(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "角色分配信息", required = true)
            @Valid @RequestBody AssignRoleDTO dto) {
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
    @Operation(summary = "重置用户密码", description = "重置指定用户的密码，需要管理员权限")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "重置成功"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public R<Boolean> resetPassword(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "密码重置信息", required = true)
            @Valid @RequestBody ResetPasswordDTO dto) {
        log.info("重置密码请求: userId={}", userId);
        dto.setUserId(userId);
        Boolean result = userService.resetPassword(dto);
        return R.success(result);
    }
}
