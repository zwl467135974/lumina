package io.lumina.base.service;

import io.lumina.base.api.dto.apitoken.CreateApiTokenDTO;
import io.lumina.base.api.vo.apitoken.ApiTokenUserVO;
import io.lumina.base.api.vo.apitoken.ApiTokenVO;

import java.util.List;

/**
 * API Token 服务接口
 *
 * <p>管理外部调用（OpenAI 兼容端点）的 API Token：创建、校验、撤销、列表。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
public interface ApiTokenService {

    /**
     * 创建 API Token
     *
     * <p>明文 Token（sk- 开头）只在本次返回中携带一次，DB 仅存 SHA-256 哈希。
     *
     * @param dto 创建参数
     * @return Token VO（含明文）
     */
    ApiTokenVO createToken(CreateApiTokenDTO dto);

    /**
     * 校验 API Token（Gateway/Filter 调用）
     *
     * <p>校验通过走 Redis 缓存（TTL 5 分钟），未命中回源 DB 并异步更新 last_used_at。
     *
     * @param cleartext Token 明文
     * @return 关联用户信息，无效/过期/禁用时返回 null
     */
    ApiTokenUserVO validateToken(String cleartext);

    /**
     * 撤销 API Token（仅限本人）
     *
     * <p>删除 DB 记录并立即失效 Redis 缓存。
     *
     * @param id Token ID
     */
    void revokeToken(Long id);

    /**
     * 查询当前用户的 Token 列表（不含哈希与明文）
     *
     * @param userId 用户 ID
     * @return Token 列表
     */
    List<ApiTokenVO> listTokens(Long userId);
}
