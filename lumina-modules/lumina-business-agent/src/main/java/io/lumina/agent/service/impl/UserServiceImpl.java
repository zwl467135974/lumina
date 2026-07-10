package io.lumina.agent.service.impl;

import io.lumina.agent.api.dto.LoginDTO;
import io.lumina.agent.api.vo.LoginVO;
import io.lumina.agent.domain.model.User;
import io.lumina.agent.infrastructure.entity.UserDO;
import io.lumina.agent.infrastructure.mapper.UserMapper;
import io.lumina.agent.service.UserService;
import io.lumina.common.core.LoginUser;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.util.JwtUtil;
import io.lumina.common.util.PasswordUtil;
import io.lumina.framework.cache.RedisCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
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

    private static final String LOGIN_FAIL_KEY_PREFIX = "login:fail:";
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisCacheManager redisCacheManager;

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

        String failKey = LOGIN_FAIL_KEY_PREFIX + loginDTO.getUsername();
        Long failCount = redisCacheManager.get(failKey);
        if (failCount != null && failCount >= MAX_LOGIN_ATTEMPTS) {
            log.warn("账号已被锁定: {}", loginDTO.getUsername());
            throw new BusinessException(ErrorCode.USER_LOCKED);
        }

        UserDO userDO = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getUsername, loginDTO.getUsername())
        );

        if (userDO == null) {
            recordLoginFailure(failKey);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        if (!PasswordUtil.verify(loginDTO.getPassword(), userDO.getPassword())) {
            recordLoginFailure(failKey);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        if (userDO.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        redisCacheManager.delete(failKey);

        User user = toDomain(userDO);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("username", user.getUsername());
        claims.put("tenantId", 0L);
        claims.put("roles", user.getRole() != null ? user.getRole() : "");
        claims.put("permissions", "");

        String token = jwtUtil.generateToken(user.getUsername(), claims);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setTokenType("Bearer");
        loginVO.setUserId(user.getUserId());
        loginVO.setUsername(user.getUsername());
        loginVO.setRealName(user.getRealName());
        loginVO.setRole(user.getRole());
        loginVO.setExpiration(jwtUtil.getExpiration(token).getTime());

        log.info("用户登录成功: userId={}, username={}", user.getUserId(), user.getUsername());

        redisCacheManager.zAdd("online:users", System.currentTimeMillis(),
                user.getUserId() + ":" + user.getUsername());

        return loginVO;
    }

    private void recordLoginFailure(String failKey) {
        long count = redisCacheManager.incrementAndGet(failKey);
        if (count == 1) {
            redisCacheManager.expire(failKey, LOCK_DURATION);
        }
        log.warn("登录失败次数: key={}, count={}", failKey, count);
    }

    @Override
    public User getUserById(Long userId) {
        UserDO userDO = userMapper.selectById(userId);
        if (userDO == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在: id=" + userId);
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
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在: username=" + username);
        }

        return toDomain(userDO);
    }
}
