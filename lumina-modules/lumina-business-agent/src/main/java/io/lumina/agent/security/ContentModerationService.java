package io.lumina.agent.security;

/**
 * 内容审核服务接口
 *
 * <p>定义内容审核的可插拔接口，默认实现基于规则引擎，
 * 可替换为第三方审核 API（如阿里云内容安全、Azure Content Safety）。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface ContentModerationService {

    /**
     * 审核文本内容
     *
     * @param text 待审核文本
     * @return 审核结果
     */
    ModerationResult moderate(String text);

    /**
     * 审核文本内容（指定审核级别）
     *
     * @param text   待审核文本
     * @param strict 是否严格模式
     * @return 审核结果
     */
    default ModerationResult moderate(String text, boolean strict) {
        return moderate(text);
    }
}
