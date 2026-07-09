package io.lumina.base.service.impl;

import io.lumina.base.api.vo.user.OnlineUserVO;
import io.lumina.base.service.OnlineUserService;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 在线用户业务服务实现
 * <p>
 * 基于 Redis Sorted Set 维护在线状态。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserServiceImpl implements OnlineUserService {

    private static final String ONLINE_KEY = "online:users";
    private static final String ONLINE_TOKEN_PREFIX = "online:user:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public List<OnlineUserVO> listOnline(String username) {
        Set<Object> members = redisTemplate.opsForZSet().range(ONLINE_KEY, 0, -1);
        if (members == null || members.isEmpty()) {
            return List.of();
        }

        List<OnlineUserVO> result = new ArrayList<>();
        for (Object member : members) {
            String entry = String.valueOf(member);
            String[] parts = entry.split(":", 2);
            if (parts.length < 2) continue;

            Long userId = Long.parseLong(parts[0]);
            String uname = parts[1];

            // 用户名筛选
            if (StringUtils.hasText(username) && !uname.toLowerCase().contains(username.toLowerCase())) {
                continue;
            }

            // 获取登录时间（ZSET score）
            Double score = redisTemplate.opsForZSet().score(ONLINE_KEY, member);
            LocalDateTime loginTime = null;
            if (score != null) {
                loginTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(score.longValue()), ZoneId.systemDefault());
            }

            OnlineUserVO vo = new OnlineUserVO();
            vo.setUserId(userId);
            vo.setUsername(uname);
            vo.setLoginTime(loginTime);
            result.add(vo);
        }
        return result;
    }

    @Override
    public void forceLogout(Long userId) {
        // 查找该用户的在线记录
        Set<Object> members = redisTemplate.opsForZSet().range(ONLINE_KEY, 0, -1);
        if (members != null) {
            for (Object member : members) {
                String entry = String.valueOf(member);
                if (entry.startsWith(userId + ":")) {
                    redisTemplate.opsForZSet().remove(ONLINE_KEY, member);
                    break;
                }
            }
        }
        // 删除 token 记录
        redisTemplate.delete(ONLINE_TOKEN_PREFIX + userId + ":token");
        log.info("强制下线用户: userId={}", userId);
    }

    @Override
    public void recordLogin(Long userId, String username) {
        String member = userId + ":" + username;
        redisTemplate.opsForZSet().add(ONLINE_KEY, member, System.currentTimeMillis());
        log.info("记录用户登录: userId={}, username={}", userId, username);
    }

    @Override
    public void recordLogout(Long userId) {
        Set<Object> members = redisTemplate.opsForZSet().range(ONLINE_KEY, 0, -1);
        if (members != null) {
            for (Object member : members) {
                if (String.valueOf(member).startsWith(userId + ":")) {
                    redisTemplate.opsForZSet().remove(ONLINE_KEY, member);
                    break;
                }
            }
        }
        redisTemplate.delete(ONLINE_TOKEN_PREFIX + userId + ":token");
    }
}
