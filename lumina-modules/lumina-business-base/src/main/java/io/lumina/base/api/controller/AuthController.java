package io.lumina.base.api.controller;

import io.lumina.base.api.dto.LoginDTO;
import io.lumina.base.api.vo.LoginVO;
import io.lumina.base.service.AuthService;
import io.lumina.common.core.R;
import io.lumina.common.core.LoginUser;
import io.lumina.common.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 认证 Controller
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Tag(name = "认证管理", description = "用户登录、登出、Token 管理等接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/base/auth")
@Validated
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，成功后返回JWT Token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "登录成功", content = @Content(schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "401", description = "用户名或密码错误", content = @Content),
        @ApiResponse(responseCode = "400", description = "请求参数错误", content = @Content)
    })
    public R<LoginVO> login(
            @Parameter(description = "登录信息", required = true)
            @Valid @RequestBody LoginDTO loginDTO) {
        log.info("用户登录请求: {}", loginDTO.getUsername());

        LoginVO loginVO = authService.login(loginDTO);

        return R.success(loginVO);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/user-info")
    @Operation(summary = "获取当前用户信息", description = "根据JWT Token获取当前登录用户的详细信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "Token无效或已过期", content = @Content)
    })
    public R<LoginUser> getUserInfo(
            @Parameter(description = "JWT Token，格式：Bearer {token}", required = true, example = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
            @RequestHeader("Authorization") String authorization) {
        log.info("获取当前用户信息");

        // 从 Authorization header 中提取 token
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return R.fail(401, "未提供有效的 Token");
        }

        String token = authorization.substring(7);

        // 验证并解析 token
        try {
            LoginUser loginUser = JwtUtil.parseTokenToLoginUser(token);

            return R.success(loginUser);
        } catch (Exception e) {
            log.error("Token 解析失败", e);
            return R.fail(401, "Token 无效或已过期");
        }
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "用户退出登录，Token 将失效")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "登出成功")
    })
    public R<Void> logout() {
        log.info("用户登出");

        authService.logout();

        return R.success();
    }
}
