package io.lumina.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.lumina.common.core.LoginUser;
import lombok.extern.slf4j.Slf4j;

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
public class JwtUtil {

    /**
     * JWT 密钥（生产环境应从配置文件读取）
     */
    private static final String SECRET_KEY = "lumina-secret-key-for-jwt-token-generation-must-be-long-enough";

    /**
     * Token 过期时间（7天）
     */
    private static final long EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000;

    /**
     * 生成密钥
     */
    private static SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Token
     *
     * @param subject   主题（通常是用户ID或用户名）
     * @param claims    自定义声明
     * @param expiration 过期时间（毫秒）
     * @return JWT Token
     */
    public static String generateToken(String subject, Map<String, Object> claims, Long expiration) {
        long exp = expiration != null ? expiration : EXPIRATION_TIME;
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
    public static String generateToken(String subject, Map<String, Object> claims) {
        return generateToken(subject, claims, EXPIRATION_TIME);
    }

    /**
     * 生成 Token（无自定义声明）
     */
    public static String generateToken(String subject) {
        return generateToken(subject, null, EXPIRATION_TIME);
    }

    /**
     * 解析 Token
     *
     * @param token JWT Token
     * @return Claims
     */
    public static Claims parseToken(String token) {
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
    public static boolean validateToken(String token) {
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
    public static String getSubject(String token) {
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
    public static Object getClaim(String token, String key) {
        Claims claims = parseToken(token);
        return claims.get(key);
    }

    /**
     * 刷新 Token
     *
     * @param token 旧的 Token
     * @return 新的 Token
     */
    public static String refreshToken(String token) {
        Claims claims = parseToken(token);
        String subject = claims.getSubject();
        Map<String, Object> newClaims = claims;
        return generateToken(subject, newClaims, EXPIRATION_TIME);
    }

    /**
     * 获取 Token 过期时间
     *
     * @param token JWT Token
     * @return 过期时间
     */
    public static Date getExpiration(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration();
    }

    /**
     * 检查 Token 是否即将过期（1小时内）
     *
     * @param token JWT Token
     * @return 是否即将过期
     */
    public static boolean isTokenExpiringSoon(String token) {
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
    public static LoginUser parseTokenToLoginUser(String token) {
        Claims claims = parseToken(token);

        LoginUser loginUser = new LoginUser();
        loginUser.setToken(token);

        // 从 Claims 中提取用户信息
        Object userId = claims.get("userId");
        if (userId instanceof Number) {
            loginUser.setUserId(((Number) userId).longValue());
        }

        loginUser.setUsername(claims.getSubject());

        // 提取角色信息
        Object role = claims.get("role");
        if (role instanceof String) {
            loginUser.setRole((String) role);
        } else if (role instanceof String[]) {
            loginUser.setRoles((String[]) role);
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
