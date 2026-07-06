package io.lumina.agent.security;

import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt 注入检测过滤器
 *
 * <p>基于规则引擎检测常见的 Prompt 注入攻击模式，阻止恶意输入到达 LLM。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class PromptInjectionFilter {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+(instructions?|prompts?|rules?)"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?prior\\s+(instructions?|prompts?)"),
            Pattern.compile("(?i)forget\\s+(everything|all\\s+previous)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an)\\s+(different|new)"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)\\[system\\]\\s*(prompt|instruction)"),
            Pattern.compile("(?i)reveal\\s+(your\\s+)?(system\\s+)?prompt"),
            Pattern.compile("(?i)show\\s+me\\s+your\\s+(instructions?|system\\s+prompt|rules?)"),
            Pattern.compile("(?i)jailbreak", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)DAN\\s+mode", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)pretend\\s+(you\\s+are|to\\s+be)\\s+(a|an)?\\s*(different|unrestricted|unfiltered)")
    );

    private static final List<String> HIGH_RISK_KEYWORDS = List.of(
            "<|im_start|>", "<|endoftext|>", "[INST]", "[/INST]"
    );

    /**
     * 检测输入是否包含 Prompt 注入
     *
     * @param input 用户输入
     * @throws BusinessException 检测到注入时抛出
     */
    public void check(String input) {
        if (input == null || input.isBlank()) {
            return;
        }

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("检测到 Prompt 注入尝试: pattern={}", pattern.pattern());
                throw new BusinessException(ErrorCode.BAD_REQUEST, "输入包含潜在的安全风险，请修改后重试");
            }
        }

        String lowerInput = input.toLowerCase();
        for (String keyword : HIGH_RISK_KEYWORDS) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                log.warn("检测到高风险关键词: {}", keyword);
                throw new BusinessException(ErrorCode.BAD_REQUEST, "输入包含不允许的特殊标记");
            }
        }
    }
}
