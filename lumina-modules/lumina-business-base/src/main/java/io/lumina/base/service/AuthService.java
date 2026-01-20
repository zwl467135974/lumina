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
     * 用户登出
     */
    void logout();
}
