package io.lumina.agent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 默认内容审核服务实现（基于规则引擎）
 *
 * <p>检测以下类别的不当内容：
 * <ul>
 *   <li>暴力/伤害：武器制造、人身伤害描述</li>
 *   <li>违法活动：毒品、诈骗相关描述</li>
 *   <li>仇恨言论：歧视性语言</li>
 *   <li>敏感个人信息：身份证号、银行卡号（补充 OutputSanitizer 的输入侧检测）</li>
 * </ul>
 *
 * <p>可通过 {@code lumina.agent.content-moderation.enabled=false} 禁用。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class DefaultContentModerationService implements ContentModerationService {

    @Value("${lumina.agent.content-moderation.enabled:true}")
    private boolean enabled;

    private static final List<CategoryRule> RULES = List.of(
            new CategoryRule("violence",
                    "暴力/伤害",
                    List.of(
                            Pattern.compile("(?i)(制造|制作|如何造).*(炸弹|武器|枪支|弹药)"),
                            Pattern.compile("(?i)(杀人|伤害|攻击).*(方法|方式|怎么)"),
                            Pattern.compile("(?i)(自残|自杀).*(方法|方式|怎么)")
                    )),
            new CategoryRule("illegal",
                    "违法活动",
                    List.of(
                            Pattern.compile("(?i)(买卖|出售|购买).*(毒品|大麻|海洛因|冰毒)"),
                            Pattern.compile("(?i)(诈骗|骗局).*(教程|方法|如何)")
                    )),
            new CategoryRule("hate",
                    "仇恨言论",
                    List.of(
                            Pattern.compile("(?i)(仇恨|歧视).*(种族|民族|宗教|性别)"),
                            Pattern.compile("(?i)(杀光|灭绝).*(种族|民族|群体)")
                    )),
            new CategoryRule("pii",
                    "敏感信息",
                    List.of(
                            Pattern.compile("\\b\\d{15,18}[0-9Xx]\\b"),
                            Pattern.compile("\\b\\d{16,19}\\b"),
                            Pattern.compile("(?i)(password|密码|secret|密钥).{0,10}[=:].{8,}")
                    ))
    );

    @Override
    public ModerationResult moderate(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return ModerationResult.allowed();
        }

        for (CategoryRule rule : RULES) {
            for (Pattern pattern : rule.patterns()) {
                if (pattern.matcher(text).find()) {
                    log.warn("内容审核拦截: category={}, rule={}", rule.category(), rule.description());
                    return ModerationResult.blocked(
                            String.format("内容包含%s相关敏感内容，请修改后重试", rule.description()),
                            rule.category(),
                            0.9
                    );
                }
            }
        }

        return ModerationResult.allowed();
    }

    @Override
    public ModerationResult moderate(String text, boolean strict) {
        if (!enabled) {
            return ModerationResult.allowed();
        }
        ModerationResult result = moderate(text);
        if (strict && result.isAllowed() && text != null) {
            String lower = text.toLowerCase();
            for (CategoryRule rule : RULES) {
                for (var pattern : rule.patterns()) {
                    var matcher = pattern.matcher(lower);
                    if (matcher.find()) {
                        log.warn("内容审核拦截（strict 模式）: category={}, rule={}", rule.category(), rule.description());
                        return ModerationResult.blocked(
                                String.format("内容包含%s相关敏感内容，请修改后重试", rule.description()),
                                rule.category(),
                                0.95
                        );
                    }
                }
            }
        }
        return result;
    }

    private record CategoryRule(String category, String description, List<Pattern> patterns) {}
}
