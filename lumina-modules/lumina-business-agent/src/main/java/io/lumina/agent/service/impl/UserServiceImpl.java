package io.lumina.agent.service.impl;

import io.lumina.agent.api.dto.LoginDTO;
import io.lumina.agent.api.vo.LoginVO;
import io.lumina.agent.domain.model.User;
import io.lumina.agent.infrastructure.entity.UserDO;
import io.lumina.agent.infrastructure.mapper.UserMapper;
import io.lumina.agent.service.UserService;
import io.lumina.common.core.LoginUser;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.util.JwtUtil;
import io.lumina.common.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户服务实现
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * Domain -> DO 转换
     */
    private UserDO toDO(User user) {
        UserDO userDO = new UserDO();
        BeanUtils.copyProperties(user, userDO);
        return userDO;
    }

    /**
     * DO -> Domain 转换
     */
    private User toDomain(UserDO userDO) {
        User user = new User();
        BeanUtils.copyProperties(userDO, user);
        return user;
    }

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        log.info("用户登录: {}", loginDTO.getUsername());

        // 查询用户
        UserDO userDO = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getUsername, loginDTO.getUsername())
        );

        if (userDO == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 验证密码（使用 BCrypt 加密验证）
        if (!PasswordUtil.verify(loginDTO.getPassword(), userDO.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 检查用户状态
        if (userDO.getStatus() == 0) {
            throw new BusinessException("用户已被禁用");
        }

        // 转换为领域模型
        User user = toDomain(userDO);

        // 生成 JWT Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole());

        String token = JwtUtil.generateToken(user.getUsername(), claims);

        // 构建响应
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setTokenType("Bearer");
        loginVO.setUserId(user.getUserId());
        loginVO.setUsername(user.getUsername());
        loginVO.setRealName(user.getRealName());
        loginVO.setRole(user.getRole());
        loginVO.setExpiration(System.currentTimeMillis() + JwtUtil.EXPIRATION_TIME);

        log.info("用户登录成功: userId={}, username={}", user.getUserId(), user.getUsername());

        return loginVO;
    }

    @Override
    public User getUserById(Long userId) {
        UserDO userDO = userMapper.selectById(userId);
        if (userDO == null) {
            throw new BusinessException("用户不存在: id=" + userId);
        }
        return toDomain(userDO);
    }

    @Override
    public User getUserByUsername(String username) {
        UserDO userDO = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getUsername, username)
        );

        if (userDO == null) {
            throw new BusinessException("用户不存在: username=" + username);
        }

        return toDomain(userDO);
    }
}
