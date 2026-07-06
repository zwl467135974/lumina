package io.lumina.agent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 输出敏感信息脱敏过滤器
 *
 * <p>对 LLM 输出中的手机号、身份证号、银行卡号、邮箱等 PII 进行正则匹配并脱敏。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class OutputSanitizer {

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<![0-9])1[3-9]\\d{9}(?![0-9])");

    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("(?<![0-9])\\d{17}[0-9Xx](?![0-9Xx])");

    private static final Pattern BANK_CARD_PATTERN =
            Pattern.compile("(?<![0-9])\\d{16,19}(?![0-9])");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /**
     * 对输出文本进行脱敏
     *
     * @param output LLM 原始输出
     * @return 脱敏后的文本
     */
    public String sanitize(String output) {
        if (output == null || output.isEmpty()) {
            return output;
        }

        String result = output;

        result = PHONE_PATTERN.matcher(result).replaceAll(match -> {
            String phone = match.group();
            log.debug("脱敏手机号: {}****{}", phone.substring(0, 3), phone.substring(7));
            return phone.substring(0, 3) + "****" + phone.substring(7);
        });

        result = ID_CARD_PATTERN.matcher(result).replaceAll(match -> {
            String id = match.group();
            log.debug("脱敏身份证号: {}********{}", id.substring(0, 6), id.substring(14));
            return id.substring(0, 6) + "********" + id.substring(14);
        });

        result = BANK_CARD_PATTERN.matcher(result).replaceAll(match -> {
            String card = match.group();
            if (card.length() < 16) {
                return match.group();
            }
            log.debug("脱敏银行卡号: ****{}", card.substring(card.length() - 4));
            return "****" + card.substring(card.length() - 4);
        });

        result = EMAIL_PATTERN.matcher(result).replaceAll(match -> {
            String email = match.group();
            int atIndex = email.indexOf('@');
            if (atIndex <= 1) {
                return email;
            }
            return email.charAt(0) + "***" + email.substring(atIndex);
        });

        return result;
    }
}
