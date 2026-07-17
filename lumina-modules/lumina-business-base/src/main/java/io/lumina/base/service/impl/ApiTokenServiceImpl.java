package io.lumina.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.lumina.base.api.dto.apitoken.CreateApiTokenDTO;
import io.lumina.base.api.vo.apitoken.ApiTokenUserVO;
import io.lumina.base.api.vo.apitoken.ApiTokenVO;
import io.lumina.base.infrastructure.entity.ApiTokenDO;
import io.lumina.base.infrastructure.entity.UserDO;
import io.lumina.base.infrastructure.mapper.ApiTokenMapper;
import io.lumina.base.infrastructure.mapper.UserMapper;
import io.lumina.base.service.ApiTokenService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.lumina.framework.cache.RedisCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * API Token 服务实现
 *
 * <p>明文格式：{@code sk-} + 32 位 Base62 随机串；DB 仅存 SHA-256 哈希。
 * 校验结果经 {@link RedisCacheManager} 缓存 5 分钟，撤销时立即失效。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiTokenServiceImpl implements ApiTokenService {

    /**
     * Token 明文前缀
     */
    private static final String TOKEN_PREFIX = "sk-";

    /**
     * 随机部分长度（Base62 字符数）
     */
    private static final int TOKEN_RANDOM_LENGTH = 32;

    /**
     * Base62 字符表
     */
    private static final String BASE62_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /**
     * 校验缓存 Key 前缀
     */
    private static final String TOKEN_CACHE_KEY_PREFIX = "api_token:";

    /**
     * 校验缓存 TTL
     */
    private static final Duration TOKEN_CACHE_TTL = Duration.ofMinutes(5);

    /**
     * Token 用户默认角色（避免赋予管理角色）
     */
    private static final String DEFAULT_TOKEN_ROLE = "API_CLIENT";

    /**
     * 默认权限范围
     */
    private static final String DEFAULT_SCOPES = "agent:execute";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiTokenMapper apiTokenMapper;

    private final UserMapper userMapper;

    private final RedisCacheManager redisCacheManager;

    /**
     * 异步更新 last_used_at 用线程池（复用审计线程池，写失败不影响主流程）
     */
    @org.springframework.beans.factory.annotation.Autowired
    @Qualifier("auditExecutor")
    private Executor auditExecutor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiTokenVO createToken(CreateApiTokenDTO dto) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;

        // 生成明文并哈希（明文只在本次返回，DB 不存）
        String cleartext = TOKEN_PREFIX + randomBase62(TOKEN_RANDOM_LENGTH);
        String hash = sha256Hex(cleartext);

        ApiTokenDO tokenDO = new ApiTokenDO();
        tokenDO.setTokenHash(hash);
        tokenDO.setUserId(userId);
        tokenDO.setTenantId(tenantId);
        tokenDO.setName(dto.getName());
        tokenDO.setScopes(StringUtils.hasText(dto.getScopes()) ? dto.getScopes() : DEFAULT_SCOPES);
        tokenDO.setStatus(1);
        if (dto.getExpiresInDays() != null) {
            tokenDO.setExpiresAt(LocalDateTime.now().plusDays(dto.getExpiresInDays()));
        }
        apiTokenMapper.insert(tokenDO);

        log.info("创建 API Token: id={}, userId={}, name={}", tokenDO.getId(), userId, dto.getName());

        ApiTokenVO vo = toVO(tokenDO);
        vo.setToken(cleartext);
        return vo;
    }

    @Override
    public ApiTokenUserVO validateToken(String cleartext) {
        if (!StringUtils.hasText(cleartext) || !cleartext.startsWith(TOKEN_PREFIX)) {
            return null;
        }
        String hash = sha256Hex(cleartext);
        String cacheKey = TOKEN_CACHE_KEY_PREFIX + hash;

        // 先查 Redis 缓存（命中时仍需二次校验过期时间）
        ApiTokenUserVO cached = redisCacheManager.get(cacheKey);
        if (cached != null) {
            if (isExpired(cached.getExpiresAt())) {
                redisCacheManager.delete(cacheKey);
                return null;
            }
            return cached;
        }

        // 回源 DB
        ApiTokenDO tokenDO = apiTokenMapper.selectValidByHash(hash);
        if (tokenDO == null || isExpired(tokenDO.getExpiresAt())) {
            return null;
        }

        UserDO userDO = userMapper.selectByUserId(tokenDO.getUserId());
        if (userDO == null || userDO.getStatus() == null || userDO.getStatus() != 1) {
            log.warn("API Token 关联用户不存在或已禁用: tokenId={}, userId={}", tokenDO.getId(), tokenDO.getUserId());
            return null;
        }

        ApiTokenUserVO userVO = new ApiTokenUserVO();
        userVO.setUserId(userDO.getUserId());
        userVO.setUsername(userDO.getUsername());
        userVO.setTenantId(tokenDO.getTenantId());
        userVO.setScopes(tokenDO.getScopes());
        userVO.setRoles(DEFAULT_TOKEN_ROLE);
        userVO.setExpiresAt(tokenDO.getExpiresAt());

        redisCacheManager.set(cacheKey, userVO, TOKEN_CACHE_TTL);

        // 异步更新最后使用时间（不阻塞校验；缓存命中期间不重复更新，粒度约 5 分钟）
        Long tokenId = tokenDO.getId();
        auditExecutor.execute(() -> {
            try {
                apiTokenMapper.updateLastUsedAt(tokenId);
            } catch (Exception e) {
                log.debug("更新 API Token 最后使用时间失败（不影响校验）: id={}, error={}", tokenId, e.getMessage());
            }
        });

        return userVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeToken(Long id) {
        Long userId = BaseContext.getUserId();
        ApiTokenDO tokenDO = apiTokenMapper.selectById(id);
        if (tokenDO == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Token 不存在 id=" + id);
        }
        if (userId == null || !userId.equals(tokenDO.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能撤销自己的 API Token");
        }

        apiTokenMapper.deleteById(id);
        // 立即失效缓存，保证撤销后下一次调用返回 401
        redisCacheManager.delete(TOKEN_CACHE_KEY_PREFIX + tokenDO.getTokenHash());

        log.info("撤销 API Token: id={}, userId={}", id, userId);
    }

    @Override
    public List<ApiTokenVO> listTokens(Long userId) {
        LambdaQueryWrapper<ApiTokenDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiTokenDO::getUserId, userId);
        wrapper.orderByDesc(ApiTokenDO::getCreateTime);
        return apiTokenMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * DO → VO 转换（不含哈希与明文）
     */
    private ApiTokenVO toVO(ApiTokenDO tokenDO) {
        ApiTokenVO vo = new ApiTokenVO();
        BeanUtils.copyProperties(tokenDO, vo);
        return vo;
    }

    /**
     * 判断是否已过期（null=永不过期）
     */
    private boolean isExpired(LocalDateTime expiresAt) {
        return expiresAt != null && !expiresAt.isAfter(LocalDateTime.now());
    }

    /**
     * 生成 Base62 随机串
     */
    private String randomBase62(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(BASE62_ALPHABET.charAt(SECURE_RANDOM.nextInt(BASE62_ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * SHA-256 哈希（hex 小写，64 字符）
     */
    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.CRYPTO_FAILED, "SHA-256 算法不可用", e);
        }
    }
}
