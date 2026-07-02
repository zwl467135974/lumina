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
import io.lumina.agent.infrastructure.entity.KnowledgeDocumentDO;
import io.lumina.agent.infrastructure.mapper.KnowledgeDocumentMapper;
import io.lumina.framework.config.RocketMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

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

            log.info("文档异步处理完成: uuid={}, chunks={}", msg.getUuid(), docs != null ? docs.size() : 0);

        } catch (Exception e) {
            log.error("文档异步处理失败: {}", msg.getUuid(), e);
            updateStatus(msg.getUuid(), 2, 0, null);
        }
    }

    private List<Document> parseDocument(Path filePath, String format, int chunkSize, int overlap) {
        ReaderInput input = ReaderInput.fromPath(filePath);
        switch (format) {
            case "pdf":
                return new PDFReader(chunkSize, SplitStrategy.PARAGRAPH, overlap).read(input).block();
            case "doc":
            case "docx":
                return new WordReader(chunkSize, SplitStrategy.PARAGRAPH, overlap,
                        false, true, io.agentscope.core.rag.reader.TableFormat.MARKDOWN).read(input).block();
            default:
                return new TextReader(chunkSize, SplitStrategy.PARAGRAPH, overlap).read(input).block();
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
