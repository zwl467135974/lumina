package io.lumina.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 多模态图片内容
 *
 * <p>用于向 AgentScope 传递 Base64 图片内容，不依赖 Web 层类型。
 *
 * <p>实现 {@link MultimodalContent} 密封接口，与 {@link MultimodalDocument} 共同构成
 * 多模态内容体系。引擎层按 {@code instanceof} 分发到 {@code ImageBlock}。
 *
 * <p>图片历史卸载（v3.13）：图片本体只在当前轮进入请求，落历史记忆时仅保留
 * {@link #buildReferenceNote} 生成的引用标记（文件名 + fileUuid）——后续轮次
 * 模型可感知"当时有图"，需要视觉内容时由用户重新提供文件。
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

    /**
     * 来源文件 UUID（文件服务侧的持久化标识，可空）。
     *
     * <p>业务层从文件服务加载图片时填入；引用标记依赖它，缺失时标记退化为仅文件名。
     */
    private String fileUuid;

    /**
     * 来源文件原始名（可空，仅用于引用标记的可读性）。
     */
    private String originalName;

    /**
     * 兼容旧调用方的双参构造（元数据缺省）。
     */
    public MultimodalImage(String mediaType, String data) {
        this(mediaType, data, null, null);
    }

    /**
     * 生成历史引用标记（图片历史卸载的"留引用"半边）
     *
     * <p>落 user 记忆时追加到任务文本后：后续轮次模型能看到"本轮有图、文件是什么"，
     * 而图片本体（Base64）不再进入历史。无图片时返回空串。
     *
     * @param contents 本轮多模态内容（可空）
     * @return 引用标记文本（空串表示无图片），如
     *         {@code [本轮用户发送了 1 张图片: shot.png (fileUuid=a1b2)，历史轮次不再附带图片本体]}
     */
    public static String buildReferenceNote(List<MultimodalContent> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        StringBuilder images = new StringBuilder();
        int count = 0;
        for (MultimodalContent content : contents) {
            if (!(content instanceof MultimodalImage image)) {
                continue;
            }
            if (++count > 1) {
                images.append(", ");
            }
            images.append(image.originalName != null ? image.originalName : "image");
            if (image.fileUuid != null && !image.fileUuid.isBlank()) {
                images.append(" (fileUuid=").append(image.fileUuid).append(")");
            }
        }
        if (count == 0) {
            return "";
        }
        return "\n[本轮用户发送了 " + count + " 张图片: " + images
                + "。历史轮次不再附带图片本体，如需查看请让用户重新提供对应文件]";
    }
}
