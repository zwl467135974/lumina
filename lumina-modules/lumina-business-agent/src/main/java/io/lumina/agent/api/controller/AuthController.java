package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.LoginDTO;
import io.lumina.agent.api.vo.LoginVO;
import io.lumina.agent.service.UserService;
import io.lumina.common.core.R;
import io.lumina.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

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

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private io.lumina.framework.cache.RedisCacheManager redisCacheManager;

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
            io.lumina.common.core.LoginUser loginUser = jwtUtil.parseTokenToLoginUser(token);

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
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("用户登出");

        // 从 token 解析 userId，清除 Redis 在线记录
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                String token = authorization.substring(7);
                io.lumina.common.core.LoginUser loginUser = jwtUtil.parseTokenToLoginUser(token);
                if (loginUser != null && loginUser.getUserId() != null) {
                    redisCacheManager.zRemove("online:users",
                            loginUser.getUserId() + ":" + loginUser.getUsername());
                    log.info("清除在线记录: userId={}", loginUser.getUserId());
                }
            } catch (Exception e) {
                log.warn("登出时解析 token 失败（可能已过期）: {}", e.getMessage());
            }
        }

        return R.success();
    }
}
