package io.lumina.agent.security;

import io.lumina.agent.config.LuminaAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 默认输出护栏实现——基于规则的内容检查
 *
 * <p>三项检查：
 * <ol>
 *   <li>敏感关键词检测 → 拦截</li>
 *   <li>输出长度限制 → 截断重写</li>
 *   <li>重复内容检测 → 拦截（Agent 可能卡在循环中）</li>
 * </ol>
 *
 * <p>仅在 {@code lumina.agent.guardrail.enabled=true} 时生效。
 *
 * @author Lumina Team
 * @since 3.8.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lumina.agent.guardrail", name = "enabled", havingValue = "true")
public class DefaultOutputGuardrail implements OutputGuardrail {

    private final LuminaAgentProperties agentProperties;

    @Override
    public GuardrailResult check(String output, Long agentId) {
        if (output == null || output.isBlank()) {
            return GuardrailResult.pass();
        }

        // 1. 敏感关键词检测（安全优先级最高）
        io.lumina.agent.config.LuminaAgentProperties.GuardrailConfig config = agentProperties.getGuardrail();
        if (config.getBlockedKeywords() != null && !config.getBlockedKeywords().isEmpty()) {
            String lowerOutput = output.toLowerCase();
            for (String keyword : config.getBlockedKeywords()) {
                if (lowerOutput.contains(keyword.toLowerCase())) {
                    log.warn("输出护栏拦截: agentId={}, 命中关键词={}", agentId, keyword);
                    return GuardrailResult.block("输出包含敏感内容，已被护栏拦截");
                }
            }
        }

        // 2. 重复内容检测（必须在长度截断之前——否则 Agent 循环输出的重复内容
        //    会被静默截断，丢失"Agent 故障"这一关键信号）
        if (detectRepetition(output)) {
            log.warn("输出护栏拦截: agentId={}, 检测到严重重复内容", agentId);
            return GuardrailResult.block("输出包含严重重复内容，Agent 可能陷入循环");
        }

        // 3. 输出长度限制（最低优先级——纯粹的展示优化）
        int maxLen = config.getMaxOutputLength();
        if (maxLen > 0 && output.length() > maxLen) {
            String truncated = output.substring(0, maxLen) + "\n\n[输出被护栏截断：超过最大长度 " + maxLen + " 字符]";
            log.warn("输出护栏截断: agentId={}, 原长度={}, 截断到={}", agentId, output.length(), maxLen);
            return GuardrailResult.rewrite(truncated, "输出超长，已截断");
        }

        return GuardrailResult.pass();
    }

    /**
     * 检测输出是否有严重的重复模式
     *
     * <p>两条规则（阈值均可配）：
     * <ol>
     *   <li>连续 {@code repetitionConsecutiveLines}（默认 20）行完全相同 → 判定循环</li>
     *   <li>去重后唯一行数占比低于 {@code repetitionUniqueRatio}（默认 0.1，即 10%）→ 判定大量重复</li>
     * </ol>
     */
    private boolean detectRepetition(String output) {
        io.lumina.agent.config.LuminaAgentProperties.GuardrailConfig config = agentProperties.getGuardrail();
        int consecutiveThreshold = config.getRepetitionConsecutiveLines();
        double uniqueRatio = config.getRepetitionUniqueRatio();

        String[] lines = output.split("\n");
        // 行数不足时跳过连续判定，但仍可能命中唯一率判定
        Set<String> uniqueLines = new HashSet<>();
        int consecutiveDupes = 0;
        String prevLine = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(prevLine) && !trimmed.isEmpty()) {
                consecutiveDupes++;
                if (consecutiveDupes >= consecutiveThreshold) {
                    return true;
                }
            } else {
                consecutiveDupes = 0;
            }
            prevLine = trimmed;
            uniqueLines.add(trimmed);
        }

        // 如果去重后行数占比低于阈值，说明大量重复
        return lines.length > 0
                && (double) uniqueLines.size() / lines.length < uniqueRatio;
    }
}
