package io.lumina.agent.service.impl;

import io.lumina.agent.config.RagProperties;
import io.lumina.agent.infrastructure.mapper.KnowledgeDocumentMapper;
import io.lumina.common.core.BaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KnowledgeServiceImpl 单元测试
 *
 * <p>覆盖不依赖 MyBatis-Plus Lambda 缓存的纯逻辑方法。
 * 涉及 LambdaQueryWrapper 的 CRUD 方法（getDocumentStatus/listDocuments/deleteDocument）
 * 由 KnowledgeServiceIntegrationTest 集成测试覆盖。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeServiceImplTest {

    @Mock
    private KnowledgeDocumentMapper documentMapper;

    @Mock
    private RagProperties ragProperties;

    @InjectMocks
    private KnowledgeServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        BaseContext.setTenantId(1L);
        setField("chunkSize", 512);
        setField("overlap", 50);
        setField("scoreThreshold", 0.3);
        setField("storagePath", "./data/knowledge");
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    private void setField(String name, Object value) throws Exception {
        Field field = KnowledgeServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    // ==================== search ====================

    @Test
    void searchWithoutKnowledgeReturnsEmpty() throws Exception {
        // knowledge 是 @Autowired(required=false)，默认 null
        setField("knowledge", null);

        List<Map<String, Object>> result = service.search("查询内容", 5);

        assertThat(result).isEmpty();
    }

    // ==================== getFormat（private 方法反射测试） ====================

    @Test
    void getFormatRecognizesExtensions() throws Exception {
        Method method = KnowledgeServiceImpl.class.getDeclaredMethod("getFormat", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "doc.pdf")).isEqualTo("pdf");
        assertThat(method.invoke(service, "doc.PDF")).isEqualTo("pdf");
        assertThat(method.invoke(service, "doc.docx")).isEqualTo("docx");
        assertThat(method.invoke(service, "doc.doc")).isEqualTo("doc");
        assertThat(method.invoke(service, "doc.md")).isEqualTo("md");
        assertThat(method.invoke(service, "doc.txt")).isEqualTo("txt");
        assertThat(method.invoke(service, "unknown.xyz")).isEqualTo("txt");
        assertThat(method.invoke(service, (Object) null)).isEqualTo("txt");
    }

    // ==================== detectLanguage（private 方法反射测试） ====================

    @Test
    void detectLanguageRecognizesCJK() throws Exception {
        Method method = KnowledgeServiceImpl.class.getDeclaredMethod("detectLanguage", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "中文文档.pdf")).isEqualTo("zh");
        assertThat(method.invoke(service, "日本語.docx")).isEqualTo("zh");
        assertThat(method.invoke(service, "english.pdf")).isEqualTo("en");
        assertThat(method.invoke(service, "test-123.md")).isEqualTo("en");
        assertThat(method.invoke(service, (Object) null)).isEqualTo("auto");
    }

    @Test
    void detectLanguageEmptyStringReturnsEn() throws Exception {
        Method method = KnowledgeServiceImpl.class.getDeclaredMethod("detectLanguage", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "")).isEqualTo("en");
    }

    // ==================== getEmbeddingModelName（private 方法反射测试） ====================

    @Test
    void getEmbeddingModelNameReturnsDefaultWhenNoConfig() throws Exception {
        Method method = KnowledgeServiceImpl.class.getDeclaredMethod("getEmbeddingModelName");
        method.setAccessible(true);

        // ragProperties 是 mock（默认返回 null），getEmbeddingModelName 应回退为 "unknown"
        assertThat(method.invoke(service)).isEqualTo("unknown");
    }
}
