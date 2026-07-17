package io.lumina.agent.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.reader.PDFReader;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.reader.WordReader;
import io.agentscope.core.rag.store.VDBStoreBase;
import io.lumina.agent.config.RagProperties;
import io.lumina.agent.infrastructure.entity.KnowledgeDocumentDO;
import io.lumina.agent.infrastructure.mapper.KnowledgeDocumentMapper;
import io.lumina.framework.config.RocketMQConfig;
import io.lumina.notification.event.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库文档异步处理消费者
 *
 * <p>接收 {@link DocumentIngestMessage}，执行文档解析 → Embedding → 向量入库 → 更新状态。
 *
 * <p>处理失败时自动重试（RocketMQ 内置重试 16 次），最终失败标记 status=2。
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rocketmq.consumer.knowledge-ingest", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        consumerGroup = RocketMQConfig.GROUP_KNOWLEDGE_INGEST,
        topic = RocketMQConfig.TOPIC_KNOWLEDGE_INGEST
)
public class DocumentIngestConsumer implements RocketMQListener<DocumentIngestMessage> {

    @Autowired(required = false)
    private Knowledge knowledge;

    @Autowired(required = false)
    private VDBStoreBase embeddingStore;

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired(required = false)
    private io.lumina.agent.infrastructure.mapper.KnowledgeChunkMapper chunkMapper;

    @Autowired(required = false)
    private RagProperties ragProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Autowired(required = false)
    private io.lumina.agent.rag.PdfOcrProcessor pdfOcrProcessor;

    @Override
    public void onMessage(DocumentIngestMessage msg) {
        log.info("开始异步处理文档: {}", msg);

        try {
            Path filePath = Path.of(msg.getFilePath());
            if (!Files.exists(filePath)) {
                log.error("文档文件不存在: {}", msg.getFilePath());
                updateStatus(msg.getUuid(), 2, 0, null);
                return;
            }

            List<Document> docs = parseDocument(filePath, msg.getFormat(), msg.getChunkSize(), msg.getOverlap());

            // 扫描件检测：PDF 解析后无任何文本内容（扫描件/图片型 PDF 无可提取文本层）
            if ((docs == null || docs.isEmpty()) && "pdf".equalsIgnoreCase(msg.getFormat())) {
                log.warn("PDF 文档无可提取文本（疑似扫描件），尝试 OCR: uuid={}", msg.getUuid());

                // 尝试 OCR 识别（OCR 未启用时返回空字符串）
                if (pdfOcrProcessor != null) {
                    String ocrText = pdfOcrProcessor.processPdf(filePath);
                    if (!ocrText.isBlank()) {
                        log.info("OCR 识别成功，文字长度: {}", ocrText.length());
                        // 将 OCR 结果转为 Document 走正常入库流程
                        docs = ocrTextToDocuments(ocrText, msg.getChunkSize(), msg.getOverlap());
                    }
                }

                // OCR 后仍为空 → 走原失败逻辑
                if (docs == null || docs.isEmpty()) {
                    updateStatus(msg.getUuid(), 2, 0, null);
                    try {
                        if (rocketMQTemplate != null) {
                            rocketMQTemplate.convertAndSend(RocketMQConfig.TOPIC_NOTIFICATION,
                                    new NotificationEvent(null, "DOCUMENT",
                                            "文档解析为空",
                                            "文档 " + msg.getUuid() + " 似乎是扫描件或图片型 PDF，无法提取文本。"
                                                    + "请上传可复制文字的电子版 PDF。",
                                            "WARN", "knowledge_document", msg.getUuid(), msg.getTenantId()));
                        }
                    } catch (Exception ex) {
                        log.warn("发送通知失败(不影响主流程): {}", ex.getMessage());
                    }
                    Files.deleteIfExists(filePath);
                    return;
                }
            }

            Files.deleteIfExists(filePath);

            if (docs != null && !docs.isEmpty() && knowledge != null) {
                knowledge.addDocuments(docs).block();
            }

            List<String> vectorDocIds = new ArrayList<>();
            if (docs != null) {
                for (Document d : docs) {
                    if (d.getId() != null) {
                        vectorDocIds.add(d.getId());
                    }
                }
            }

            String vectorDocIdsJson = objectMapper.writeValueAsString(vectorDocIds);
            updateStatus(msg.getUuid(), 1, docs != null ? docs.size() : 0, vectorDocIdsJson);

            // 双写 chunk 原文到 MySQL（混合检索关键词路）
            saveChunksToMysql(docs, msg.getUuid(), msg.getTenantId(), null);

            log.info("文档异步处理完成: uuid={}, chunks={}", msg.getUuid(), docs != null ? docs.size() : 0);
            try {
                if (rocketMQTemplate != null) {
                    rocketMQTemplate.convertAndSend(RocketMQConfig.TOPIC_NOTIFICATION,
                            new NotificationEvent(null, "DOCUMENT",
                                    "文档处理完成",
                                    "文档 " + msg.getUuid() + " 已处理完成,共 " + (docs != null ? docs.size() : 0) + " 个分块",
                                    "INFO", "knowledge_document", msg.getUuid(), msg.getTenantId()));
                }
            } catch (Exception ex) {
                log.warn("发送通知失败(不影响主流程): {}", ex.getMessage());
            }

        } catch (Exception e) {
            log.error("文档异步处理失败: {}", msg.getUuid(), e);
            updateStatus(msg.getUuid(), 2, 0, null);
            try {
                if (rocketMQTemplate != null) {
                    rocketMQTemplate.convertAndSend(RocketMQConfig.TOPIC_NOTIFICATION,
                            new NotificationEvent(null, "DOCUMENT",
                                    "文档处理失败",
                                    "文档 " + msg.getUuid() + " 处理失败: " + e.getMessage(),
                                    "ERROR", "knowledge_document", msg.getUuid(), msg.getTenantId()));
                }
            } catch (Exception ex) {
                log.warn("发送通知失败(不影响主流程): {}", ex.getMessage());
            }
        }
    }

    /**
     * 双写 chunk 原文到 MySQL（混合检索关键词路数据源）
     *
     * @since 3.3.0
     */
    private void saveChunksToMysql(List<Document> docs, String docUuid, Long tenantId, Long kbId) {
        if (chunkMapper == null || docs == null || docs.isEmpty()) {
            return;
        }
        try {
            for (int i = 0; i < docs.size(); i++) {
                Document d = docs.get(i);
                String content = d.getMetadata() != null ? d.getMetadata().getContentText() : null;
                if (content == null || content.isBlank()) {
                    continue;
                }
                io.lumina.agent.infrastructure.entity.KnowledgeChunkDO chunk =
                        new io.lumina.agent.infrastructure.entity.KnowledgeChunkDO();
                chunk.setChunkId(d.getId() != null ? d.getId() : docUuid + "_chunk_" + i);
                chunk.setDocUuid(docUuid);
                chunk.setKbId(kbId);
                chunk.setTenantId(tenantId != null ? tenantId : 0L);
                chunk.setContent(content);
                chunk.setChunkIndex(i);
                chunk.setVectorDocId(d.getId());
                chunkMapper.insert(chunk);
            }
        } catch (Exception e) {
            log.warn("chunk 原文双写 MySQL 失败（不影响向量入库）: docUuid={}, error={}", docUuid, e.getMessage());
        }
    }

    private List<Document> parseDocument(Path filePath, String format, int chunkSize, int overlap) {
        ReaderInput input = ReaderInput.fromPath(filePath);
        SplitStrategy splitStrategy = getSplitStrategy();
        switch (format) {
            case "pdf":
                return new PDFReader(chunkSize, splitStrategy, overlap).read(input).block();
            case "doc":
            case "docx":
                return new WordReader(chunkSize, splitStrategy, overlap,
                        false, true, io.agentscope.core.rag.reader.TableFormat.MARKDOWN).read(input).block();
            default:
                return new TextReader(chunkSize, splitStrategy, overlap).read(input).block();
        }
    }

    private SplitStrategy getSplitStrategy() {
        if (ragProperties != null && ragProperties.getReader() != null) {
            String strategy = ragProperties.getReader().getSplitStrategy();
            if (strategy != null) {
                try {
                    return SplitStrategy.valueOf(strategy.toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return SplitStrategy.PARAGRAPH;
    }

    /**
     * 将 OCR 识别出的文本转为 Document 列表（复用 TextReader 分块逻辑）
     */
    private List<Document> ocrTextToDocuments(String text, int chunkSize, int overlap) {
        java.nio.file.Path tempFile = null;
        try {
            tempFile = java.nio.file.Files.createTempFile("lumina_ocr_", ".txt");
            java.nio.file.Files.writeString(tempFile, text);
            ReaderInput input = ReaderInput.fromPath(tempFile);
            return new TextReader(chunkSize, getSplitStrategy(), overlap).read(input).block();
        } catch (Exception e) {
            log.warn("OCR 文本转 Document 失败: {}", e.getMessage());
            return List.of();
        } finally {
            if (tempFile != null) {
                try { java.nio.file.Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }

    private void updateStatus(String uuid, int status, int chunkCount, String vectorDocIdsJson) {
        try {
            KnowledgeDocumentDO update = new KnowledgeDocumentDO();
            update.setDocumentUuid(uuid);
            update.setStatus(status);
            update.setChunkCount(chunkCount);
            if (vectorDocIdsJson != null) {
                update.setVectorDocIds(vectorDocIdsJson);
            }

            var wrapper = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<KnowledgeDocumentDO>();
            wrapper.eq(KnowledgeDocumentDO::getDocumentUuid, uuid)
                    .set(KnowledgeDocumentDO::getStatus, status)
                    .set(KnowledgeDocumentDO::getChunkCount, chunkCount);
            if (vectorDocIdsJson != null) {
                wrapper.set(KnowledgeDocumentDO::getVectorDocIds, vectorDocIdsJson);
            }
            documentMapper.update(null, wrapper);
        } catch (Exception e) {
            log.error("更新文档状态失败: uuid={}", uuid, e);
        }
    }
}
