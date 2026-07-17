package io.lumina.base.infrastructure.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.base.infrastructure.entity.ApiTokenDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * API Token Mapper
 *
 * <p>Token 校验路径在请求早期（Gateway/Filter），此时 BaseContext 尚未初始化，
 * 校验相关 SQL 需 {@code @InterceptorIgnore(tenantLine = "true")} 跳过租户插件
 * （参考 {@link UserMapper#selectByTenantIdAndUsername}）。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Mapper
public interface ApiTokenMapper extends BaseMapper<ApiTokenDO> {

    /**
     * 按哈希查询有效 Token（启用 + 未删除；过期时间由 Service 层判断）
     *
     * @param hash SHA-256 哈希（hex）
     * @return Token 实体，不存在时返回 null
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM lumina_api_token WHERE token_hash = #{hash} AND status = 1 AND deleted = 0")
    ApiTokenDO selectValidByHash(@Param("hash") String hash);

    /**
     * 更新最后使用时间（校验路径无租户上下文，跳过租户插件）
     *
     * @param id Token ID
     * @return 影响行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE lumina_api_token SET last_used_at = NOW() WHERE id = #{id} AND deleted = 0")
    int updateLastUsedAt(@Param("id") Long id);
}
