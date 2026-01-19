package io.lumina.common.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * 密码加密工具类
 *
 * <p>基于 BCrypt 实现密码加密和验证
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public class PasswordUtil {

    /**
     * 加密密码
     *
     * @param password 明文密码
     * @return 加密后的密码
     */
    public static String hash(String password) {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray());
    }

    /**
     * 验证密码
     *
     * @param password       明文密码
     * @param hashedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean verify(String password, String hashedPassword) {
        try {
            BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hashedPassword);
            return result.verified;
        } catch (Exception e) {
            return false;
        }
    }
}
