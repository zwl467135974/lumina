package io.lumina.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 多模态图片内容
 *
 * <p>用于向 AgentScope 传递 Base64 图片内容，不依赖 Web 层类型。
 *
 * <p>实现 {@link MultimodalContent} 密封接口，与 {@link MultimodalDocument} 共同构成
 * 多模态内容体系。引擎层按 {@code instanceof} 分发到 {@code ImageBlock}。
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public non-sealed class MultimodalImage implements MultimodalContent {

    private static final long serialVersionUID = 1L;

    /**
     * 图片 MIME 类型，如 image/png、image/jpeg。
     */
    private String mediaType;

    /**
     * 图片 Base64 内容，不包含 data URL 前缀。
     */
    private String data;
}
