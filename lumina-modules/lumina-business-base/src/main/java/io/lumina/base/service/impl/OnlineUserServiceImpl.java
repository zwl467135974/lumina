package io.lumina.base.service.impl;

import io.lumina.base.api.vo.user.OnlineUserVO;
import io.lumina.base.service.OnlineUserService;
import io.lumina.framework.cache.RedisCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 在线用户业务服务实现
 * <p>
 * 基于 RedisCacheManager 的 ZSET 方法维护在线状态。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserServiceImpl implements OnlineUserService {

    private static final String ONLINE_KEY = "online:users";

    private final RedisCacheManager redisCacheManager;

    @Override
    public List<OnlineUserVO> listOnline(String username) {
        Collection<String> members = redisCacheManager.zRange(ONLINE_KEY);
        if (members == null || members.isEmpty()) {
            return List.of();
        }

        List<OnlineUserVO> result = new ArrayList<>();
        for (String entry : members) {
            String[] parts = entry.split(":", 2);
            if (parts.length < 2) continue;

            Long userId = Long.parseLong(parts[0]);
            String uname = parts[1];

            if (StringUtils.hasText(username) && !uname.toLowerCase().contains(username.toLowerCase())) {
                continue;
            }

            Double score = redisCacheManager.zScore(ONLINE_KEY, entry);
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
        Collection<String> members = redisCacheManager.zRange(ONLINE_KEY);
        if (members != null) {
            for (String entry : members) {
                if (entry.startsWith(userId + ":")) {
                    redisCacheManager.zRemove(ONLINE_KEY, entry);
                    break;
                }
            }
        }
        log.info("强制下线用户: userId={}", userId);
    }

    @Override
    public void recordLogin(Long userId, String username) {
        redisCacheManager.zAdd(ONLINE_KEY, System.currentTimeMillis(), userId + ":" + username);
        log.info("记录用户登录: userId={}, username={}", userId, username);
    }

    @Override
    public void recordLogout(Long userId) {
        Collection<String> members = redisCacheManager.zRange(ONLINE_KEY);
        if (members != null) {
            for (String entry : members) {
                if (entry.startsWith(userId + ":")) {
                    redisCacheManager.zRemove(ONLINE_KEY, entry);
                    break;
                }
            }
        }
    }
}
