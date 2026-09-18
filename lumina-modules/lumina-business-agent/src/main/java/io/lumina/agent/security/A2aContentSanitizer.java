package io.lumina.agent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 外部来源内容安检实现（复用注入检测规则）
 *
 * <p>服务于 agent-core 的 A2A 客户端等外部内容出口（{@link io.lumina.agent.security.ExternalContentSanitizer}
 * 接缝的业务层装配）：外部 Agent 返回文本 / Agent Card 内容在进入本方模型
 * 上下文前的统一安检。
 *
 * <p>fail-closed：命中注入规则时不回传任何原文片段（摘要也不行——任何
 * 片段都可能携带载荷），仅返回拦截说明与内容长度，模型可据此决定重试、
 * 换源或上报。检测规则与技能上架体检共用同一 {@link PromptInjectionFilter}。
 *
 * @author Lumina Team
 * @since 3.12.1
 */
@Slf4j
@Component
public class A2aContentSanitizer implements ExternalContentSanitizer {

    private final PromptInjectionFilter promptInjectionFilter;

    public A2aContentSanitizer(PromptInjectionFilter promptInjectionFilter) {
        this.promptInjectionFilter = promptInjectionFilter;
    }

    @Override
    public String sanitize(String content, String source) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String finding = promptInjectionFilter.detect(content);
        if (finding == null) {
            return content;
        }
        log.warn("外部内容安检拦截: source={}, finding={}, length={}", source, finding, content.length());
        return "[安全拦截] 外部内容（来源 " + source + "）未通过提示注入检测（"
                + finding + "），已按安全策略整体拦截，内容不进入上下文。"
                + "原文长度 " + content.length() + " 字符。"
                + "可建议用户检查该外部 Agent 的可信度后重试。";
    }
}
