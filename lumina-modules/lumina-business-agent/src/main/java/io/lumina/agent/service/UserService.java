package io.lumina.agent.service;

import io.lumina.agent.api.dto.LoginDTO;
import io.lumina.agent.api.vo.LoginVO;
import io.lumina.agent.domain.model.User;

/**
 * 用户服务接口
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public interface UserService {

    /**
     * 用户登录
     *
     * @param loginDTO 登录请求
     * @return 登录响应（包含 Token）
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * 根据 ID 获取用户
     *
     * @param userId 用户 ID
     * @return 用户实体
     */
    User getUserById(Long userId);

    /**
     * 根据用户名获取用户
     *
     * @param username 用户名
     * @return 用户实体
     */
    User getUserByUsername(String username);
}
