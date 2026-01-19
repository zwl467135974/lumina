package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.LoginDTO;
import io.lumina.agent.api.vo.LoginVO;
import io.lumina.agent.service.UserService;
import io.lumina.common.core.R;
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
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        log.info("用户登录请求: {}", loginDTO.getUsername());

        LoginVO loginVO = userService.login(loginDTO);

        return R.success(loginVO);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/user-info")
    public R<io.lumina.common.core.LoginUser> getUserInfo(
            @RequestHeader("Authorization") String authorization) {
        log.info("获取当前用户信息");

        // 从 Authorization header 中提取 token
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return R.fail(401, "未提供有效的 Token");
        }

        String token = authorization.substring(7);

        // 验证并解析 token
        try {
            io.lumina.common.core.LoginUser loginUser = io.lumina.common.util.JwtUtil.parseTokenToLoginUser(token);

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

        // 在 JWT 模式下，服务端不需要做太多处理
        // 客户端删除本地存储的 Token 即可
        // 如果需要实现 Token 黑名单，可以使用 Redis

        return R.success();
    }
}
