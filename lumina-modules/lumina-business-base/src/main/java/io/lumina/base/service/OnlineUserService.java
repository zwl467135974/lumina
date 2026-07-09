package io.lumina.base.service;

import io.lumina.base.api.vo.user.OnlineUserVO;

import java.util.List;

/**
 * 在线用户业务服务
 * <p>
 * 基于 Redis Sorted Set 维护在线用户状态，不依赖数据库表。
 * Redis Key: {@code online:users} (ZSET, member={userId}:{username}, score=登录时间戳)
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public interface OnlineUserService {

    /**
     * 查询在线用户列表
     *
     * @param username 用户名筛选（模糊匹配，可为空）
     * @return 在线用户列表
     */
    List<OnlineUserVO> listOnline(String username);

    /**
     * 强制下线
     *
     * @param userId 用户 ID
     */
    void forceLogout(Long userId);

    /**
     * 记录用户登录（供 AuthController 调用）
     *
     * @param userId   用户 ID
     * @param username 用户名
     */
    void recordLogin(Long userId, String username);

    /**
     * 记录用户登出（供 AuthController 调用）
     *
     * @param userId 用户 ID
     */
    void recordLogout(Long userId);
}
