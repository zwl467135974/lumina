package io.lumina.agent.model;

import java.io.Serializable;

/**
 * 多模态内容统一接口
 *
 * <p>密封接口，支持两种内容类型：
 * <ul>
 *   <li>{@link MultimodalImage} — 图片内容（Base64），映射为 {@code ImageBlock}</li>
 *   <li>{@link MultimodalDocument} — 文档内容（提取文本），映射为 {@code TextBlock}</li>
 * </ul>
 *
 * <p>引擎层 {@code buildContextMessages} 按 {@code instanceof} 分发到不同的 AgentScope ContentBlock。
 *
 * @author Lumina Team
 * @since 3.3.0
 */
public sealed interface MultimodalContent extends Serializable
        permits MultimodalImage, MultimodalDocument {
}
