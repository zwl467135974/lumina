package io.lumina.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 属性无条件注册
 *
 * <p>将 {@link RagProperties} 独立注册，不依赖 lumina.rag.enabled。
 * 这样 {@link io.lumina.agent.rag.PdfOcrProcessor}（OCR 处理器）
 * 即使在 RAG 知识库未启用时（如仅用多模态对话的 OCR）也能正常注入。
 *
 * <p>昂贵的 RAG Bean（EmbeddingModel, VDBStoreBase, Knowledge）仍由
 * {@link io.lumina.agent.rag.RagKnowledgeFactory} 按 lumina.rag.enabled 条件创建。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagPropertiesConfig {
}
