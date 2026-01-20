package io.lumina.base.api.controller;

import io.lumina.base.api.dto.LoginDTO;
import io.lumina.base.api.vo.LoginVO;
import io.lumina.base.service.AuthService;
import io.lumina.common.core.R;
import io.lumina.common.core.LoginUser;
import io.lumina.common.util.JwtUtil;
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
    public R<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        log.info("用户登录请求: {}", loginDTO.getUsername());

        LoginVO loginVO = authService.login(loginDTO);

        return R.success(loginVO);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/user-info")
    public R<LoginUser> getUserInfo(@RequestHeader("Authorization") String authorization) {
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
    public R<Void> logout() {
        log.info("用户登出");

        authService.logout();

        return R.success();
    }
}
