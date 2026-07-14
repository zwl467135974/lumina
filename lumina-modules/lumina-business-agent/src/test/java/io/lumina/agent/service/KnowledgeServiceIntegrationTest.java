package io.lumina.agent.service;

import io.lumina.agent.BaseIntegrationTest;
import io.lumina.agent.infrastructure.entity.KnowledgeDocumentDO;
import io.lumina.agent.infrastructure.mapper.KnowledgeDocumentMapper;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KnowledgeService 集成测试
 *
 * <p>验证知识文档的查询、删除、分页列表等 Mapper 相关操作。
 * 使用真实 MySQL（lumina_dev），事务回滚保证隔离。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@Transactional
class KnowledgeServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    private static final Long TENANT = 9001L;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void getDocumentStatusReturnsDocument() {
        // given: 插入一条文档记录
        KnowledgeDocumentDO doc = insertDoc("status-test-uuid", "测试.pdf", 1);

        // when
        KnowledgeDocumentDO result = knowledgeService.getDocumentStatus("status-test-uuid");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("测试.pdf");
        assertThat(result.getStatus()).isEqualTo(1);
    }

    @Test
    void getDocumentStatusReturnsNullForNotFound() {
        KnowledgeDocumentDO result = knowledgeService.getDocumentStatus("nonexistent-uuid");

        assertThat(result).isNull();
    }

    @Test
    void listDocumentsReturnsPaginatedResults() {
        insertDoc("list-1", "文档1.txt", 1);
        insertDoc("list-2", "文档2.txt", 1);

        PageResult<KnowledgeDocumentDO> result = knowledgeService.listDocuments(null, null, 1, 10);

        assertThat(result.getList()).isNotEmpty();
        assertThat(result.getPageNum()).isEqualTo(1);
    }

    @Test
    void listDocumentsFilteredByAgentId() {
        insertDoc("agent-filter-1", "Agent文档.txt", 1);

        PageResult<KnowledgeDocumentDO> result = knowledgeService.listDocuments(42L, null, 1, 10);

        // 至少有插入的那条
        assertThat(result.getList()).anyMatch(d -> d.getAgentId() != null && d.getAgentId().equals(42L));
    }

    @Test
    void deleteDocumentRemovesRecord() {
        insertDoc("del-test-uuid", "待删除.txt", 1);

        // when
        knowledgeService.deleteDocument("del-test-uuid");

        // then
        KnowledgeDocumentDO result = knowledgeService.getDocumentStatus("del-test-uuid");
        assertThat(result).isNull();
    }

    @Test
    void deleteDocumentNotFoundDoesNothing() {
        // 不崩溃即可
        knowledgeService.deleteDocument("nonexistent-del-uuid");
    }

    @Test
    void searchReturnsEmptyWithoutKnowledge() {
        // RAG 默认未启用，knowledge bean 不存在
        var results = knowledgeService.search("查询内容", 5);
        assertThat(results).isEmpty();
    }

    @Test
    void getDocumentStatusTenantIsolation() {
        // given: 插入 TENANT 的文档
        insertDoc("tenant-iso-uuid", "租户文档.txt", 1);

        // when: 切换到另一个租户
        BaseContext.setTenantId(9999L);

        // then: 查不到
        KnowledgeDocumentDO result = knowledgeService.getDocumentStatus("tenant-iso-uuid");
        assertThat(result).isNull();
    }

    // ==================== 辅助方法 ====================

    private KnowledgeDocumentDO insertDoc(String uuid, String title, int status) {
        KnowledgeDocumentDO doc = new KnowledgeDocumentDO();
        doc.setDocumentUuid(uuid);
        doc.setTenantId(TENANT);
        doc.setAgentId(42L);
        doc.setTitle(title);
        doc.setFormat("txt");
        doc.setFileSize(100L);
        doc.setLanguage("zh");
        doc.setEmbeddingModel("test-model");
        doc.setStatus(status);
        documentMapper.insert(doc);
        return doc;
    }
}
