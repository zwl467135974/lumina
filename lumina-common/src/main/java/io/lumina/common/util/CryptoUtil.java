package io.lumina.common.util;

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
public final class CryptoUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private static final byte[] KEY;

    static {
        String envKey = System.getenv("LUMINA_CRYPTO_KEY");
        if (envKey == null || envKey.isBlank()) {
            envKey = "lumina-dev-default-key-do-not-use-in-production";
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            KEY = Arrays.copyOf(sha256.digest(envKey.getBytes(StandardCharsets.UTF_8)), 32);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize CryptoUtil", e);
        }
    }

    private CryptoUtil() {
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
            throw new RuntimeException("Encryption failed", e);
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
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, ALGORITHM), new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
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
