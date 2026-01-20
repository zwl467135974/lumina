package io.lumina.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.lumina.common.core.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类
 *
 * <p>基于 jjwt 0.12.5 实现 JWT 的生成和解析。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * JWT 密钥（从配置文件读取）
     */
    @Value("${lumina.jwt.secret-key:lumina-secret-key-for-jwt-token-generation-must-be-long-enough}")
    private String secretKey;

    /**
     * Token 过期时间（从配置文件读取，默认 7 天）
     */
    @Value("${lumina.jwt.expiration:604800000}")
    private long expirationTime;

    /**
     * 生成密钥
     */
    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Token
     *
     * @param subject   主题（通常是用户ID或用户名）
     * @param claims    自定义声明
     * @param expiration 过期时间（毫秒）
     * @return JWT Token
     */
    public String generateToken(String subject, Map<String, Object> claims, Long expiration) {
        long exp = expiration != null ? expiration : expirationTime;
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + exp);

        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSignKey())
                .compact();
    }

    /**
     * 生成 Token（使用默认过期时间）
     */
    public String generateToken(String subject, Map<String, Object> claims) {
        return generateToken(subject, claims, expirationTime);
    }

    /**
     * 生成 Token（无自定义声明）
     */
    public String generateToken(String subject) {
        return generateToken(subject, null, expirationTime);
    }

    /**
     * 解析 Token
     *
     * @param token JWT Token
     * @return Claims
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSignKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("解析 Token 失败: {}", e.getMessage());
            throw new RuntimeException("无效的 Token", e);
        }
    }

    /**
     * 验证 Token 是否有效
     *
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            return expiration.after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 Token 中获取主题（用户ID或用户名）
     *
     * @param token JWT Token
     * @return 主题
     */
    public String getSubject(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * 从 Token 中获取自定义声明
     *
     * @param token JWT Token
     * @param key  声明键
     * @return 声明值
     */
    public Object getClaim(String token, String key) {
        Claims claims = parseToken(token);
        return claims.get(key);
    }

    /**
     * 刷新 Token
     *
     * @param token 旧的 Token
     * @return 新的 Token
     */
    public String refreshToken(String token) {
        Claims claims = parseToken(token);
        String subject = claims.getSubject();
        Map<String, Object> newClaims = claims;
        return generateToken(subject, newClaims, expirationTime);
    }

    /**
     * 获取 Token 过期时间
     *
     * @param token JWT Token
     * @return 过期时间
     */
    public Date getExpiration(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration();
    }

    /**
     * 检查 Token 是否即将过期（1小时内）
     *
     * @param token JWT Token
     * @return 是否即将过期
     */
    public boolean isTokenExpiringSoon(String token) {
        try {
            Date expiration = getExpiration(token);
            long timeToExpire = expiration.getTime() - System.currentTimeMillis();
            return timeToExpire < (60 * 60 * 1000); // 1小时
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 解析 Token 为 LoginUser
     *
     * @param token JWT Token
     * @return LoginUser
     */
    public LoginUser parseTokenToLoginUser(String token) {
        Claims claims = parseToken(token);

        LoginUser loginUser = new LoginUser();
        loginUser.setToken(token);

        // 从 Claims 中提取用户信息
        Object userId = claims.get("userId");
        if (userId instanceof Number) {
            loginUser.setUserId(((Number) userId).longValue());
        }

        loginUser.setUsername(claims.getSubject());

        // 提取租户 ID
        Object tenantId = claims.get("tenantId");
        if (tenantId instanceof Number) {
            loginUser.setTenantId(((Number) tenantId).longValue());
        }

        // 提取角色信息
        Object roles = claims.get("roles");
        if (roles instanceof String[]) {
            loginUser.setRoles((String[]) roles);
        } else if (roles instanceof java.util.Collection) {
            loginUser.setRoles(((java.util.Collection<?>) roles).toArray(new String[0]));
        }

        // 提取权限信息
        Object permissions = claims.get("permissions");
        if (permissions instanceof String[]) {
            loginUser.setPermissions((String[]) permissions);
        } else if (permissions instanceof java.util.Collection) {
            loginUser.setPermissions(((java.util.Collection<?>) permissions).toArray(new String[0]));
        }

        // 提取其他信息
        Object nickname = claims.get("nickname");
        if (nickname != null) {
            loginUser.setNickname(nickname.toString());
        }

        Object email = claims.get("email");
        if (email != null) {
            loginUser.setEmail(email.toString());
        }

        Object avatar = claims.get("avatar");
        if (avatar != null) {
            loginUser.setAvatar(avatar.toString());
        }

        // 设置登录时间和过期时间
        loginUser.setLoginTime(claims.getIssuedAt().getTime());
        loginUser.setExpireTime(claims.getExpiration().getTime());

        return loginUser;
    }
}
