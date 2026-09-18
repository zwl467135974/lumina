package io.lumina.base.service;

import io.lumina.base.api.dto.LoginDTO;
import io.lumina.base.api.vo.LoginVO;

/**
 * 认证服务接口
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public interface AuthService {

    /**
     * 用户登录
     *
     * @param loginDTO 登录请求
     * @return 登录响应
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * 按用户 ID 直接签发登录态（OAuth2 三方登录回调成功后复用）
     *
     * @param userId 用户 ID
     * @since 3.12.0
     */
    LoginVO loginByUserId(Long userId);

    /**
     * 用户登出
     *
     * @param token JWT Token（可为 null）
     */
    void logout(String token);
}
