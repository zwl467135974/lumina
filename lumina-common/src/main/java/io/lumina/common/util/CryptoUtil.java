package io.lumina.common.util;

import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-GCM 对称加密工具（用于 API Key 等敏感数据加密存储）
 * <p>
 * 密钥从环境变量 {@code LUMINA_CRYPTO_KEY} 读取，未设置时使用内置默认密钥（仅限开发环境）。
 */
@Slf4j
public final class CryptoUtil {

    private static final String DEFAULT_KEY = "lumina-dev-default-key-do-not-use-in-production";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private static volatile byte[] KEY;

    private static volatile boolean usingDefaultKey;

    static {
        String envKey = System.getenv("LUMINA_CRYPTO_KEY");
        if (envKey == null || envKey.isBlank()) {
            log.warn("CryptoUtil 正在使用默认开发密钥，生产环境必须通过 LUMINA_CRYPTO_KEY 环境变量配置独立密钥");
            envKey = DEFAULT_KEY;
            usingDefaultKey = true;
        }
        KEY = deriveKey(envKey);
    }

    private CryptoUtil() {
    }

    private static byte[] deriveKey(String key) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return Arrays.copyOf(sha256.digest(key.getBytes(StandardCharsets.UTF_8)), 32);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CRYPTO_FAILED, "Failed to initialize CryptoUtil", e);
        }
    }

    /**
     * 注入密钥（供 Spring 初始化时从配置中心注入，优先级高于环境变量）
     */
    public static void setKey(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        KEY = deriveKey(key);
        usingDefaultKey = DEFAULT_KEY.equals(key);
    }

    private static void warnIfDefaultKey() {
        if (usingDefaultKey) {
            log.warn("CryptoUtil 正在使用默认开发密钥加解密数据，生产环境必须通过 LUMINA_CRYPTO_KEY 配置独立密钥");
        }
    }

    /**
     * 加密
     *
     * @param plaintext 明文
     * @return Base64 编码的密文（IV + 密文）
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        warnIfDefaultKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, ALGORITHM), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CRYPTO_FAILED, "Encryption failed", e);
        }
    }

    /**
     * 解密
     *
     * @param ciphertext Base64 编码的密文（IV + 密文）
     * @return 明文
     */
    public static String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return null;
        }
        warnIfDefaultKey();
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, ALGORITHM), new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CRYPTO_FAILED, "Decryption failed", e);
        }
    }

    /**
     * 脱敏显示（保留前 4 后 4，中间用 **** 代替）
     */
    public static String mask(String plaintext) {
        if (plaintext == null || plaintext.length() <= 8) {
            return "****";
        }
        return plaintext.substring(0, 4) + "****" + plaintext.substring(plaintext.length() - 4);
    }
}
