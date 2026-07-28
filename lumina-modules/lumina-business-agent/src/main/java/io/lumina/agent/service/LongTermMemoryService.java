package io.lumina.agent.service;

import io.lumina.agent.api.vo.LongTermMemoryVO;

import java.util.List;

/**
 * 长期记忆服务
 *
 * <p>承担 Reflective Memory 提取出的长期记忆的查询与删除，
 * 内置用户鉴权（不允许跨用户访问他人记忆）。
 *
 * @author Lumina Team
 * @since 3.10.0
 */
public interface LongTermMemoryService {

    /**
     * 查询当前用户的长期记忆
     *
     * @param userId  当前登录用户 ID（由 Controller 从 BaseContext 取出并传入）
     * @param agentId 可选：按 Agent 过滤
     * @param limit   最多返回条数
     */
    List<LongTermMemoryVO> list(Long userId, Long agentId, int limit);

    /**
     * 删除单条长期记忆（带属主校验）
     *
     * @param userId 当前登录用户 ID
     * @param id     记忆 ID
     */
    void delete(Long userId, Long id);

    /**
     * 清空当前用户的全部长期记忆
     *
     * @param userId 当前登录用户 ID
     * @param agentId 可选：仅清空该 Agent 的记忆
     * @return 实际删除条数
     */
    int deleteAll(Long userId, Long agentId);
}
