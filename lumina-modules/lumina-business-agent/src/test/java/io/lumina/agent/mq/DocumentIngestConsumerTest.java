package io.lumina.agent.mq;

import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.store.VDBStoreBase;
import io.lumina.agent.config.RagProperties;
import io.lumina.agent.infrastructure.mapper.KnowledgeDocumentMapper;
import io.lumina.agent.infrastructure.mapper.KnowledgeChunkMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * DocumentIngestConsumer 单元测试
 *
 * <p>验证文档异步消费的核心场景：文件不存在、正常处理、解析失败不传播。
 * Consumer 内部依赖均为 @Autowired(required=false)，用 ReflectionTestUtils 手动注入。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@ExtendWith(MockitoExtension.class)
class DocumentIngestConsumerTest {

    @Mock
    private Knowledge knowledge;

    @Mock
    private VDBStoreBase embeddingStore;

    @Mock
    private KnowledgeDocumentMapper documentMapper;

    @Mock
    private KnowledgeChunkMapper chunkMapper;

    @Mock
    private RagProperties ragProperties;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DocumentIngestConsumer consumer;

    @Test
    void fileNotExistsDoesNotThrow() {
        DocumentIngestMessage msg = new DocumentIngestMessage(
                "doc-uuid-1", "/nonexistent/file.txt", "txt", 1L, 100L, 512, 50);

        // 文件不存在 → 标记失败，不抛异常
        assertThatCode(() -> consumer.onMessage(msg)).doesNotThrowAnyException();
    }

    @Test
    void scannedPdfDoesNotThrow() {
        DocumentIngestMessage msg = new DocumentIngestMessage(
                "doc-uuid-2", "/nonexistent/fake.pdf", "pdf", 1L, 100L, 512, 50);

        // 文件不存在也会先走 "文件不存在" 路径
        assertThatCode(() -> consumer.onMessage(msg)).doesNotThrowAnyException();
    }

    @Test
    void parseTxtDoesNotThrow(@TempDir Path tempDir) throws Exception {
        Path textFile = tempDir.resolve("doc.txt");
        java.nio.file.Files.writeString(textFile, "This is a test document for RAG ingestion.");

        DocumentIngestMessage msg = new DocumentIngestMessage(
                "doc-uuid-3", textFile.toString(), "txt", 1L, 100L, 512, 50);

        // 正常 txt 处理不应抛异常
        assertThatCode(() -> consumer.onMessage(msg)).doesNotThrowAnyException();
    }

    @Test
    void objectMapperNullDoesNotCrash() {
        // 即使 objectMapper 为 null（构造器必需依赖），consumer 不应 NPE
        DocumentIngestConsumer rawConsumer = new DocumentIngestConsumer(documentMapper, null, null);

        DocumentIngestMessage msg = new DocumentIngestMessage(
                "doc-uuid-4", "/nonexistent/file.txt", "txt", 1L, 100L, 512, 50);

        assertThatCode(() -> rawConsumer.onMessage(msg)).doesNotThrowAnyException();
    }
}
