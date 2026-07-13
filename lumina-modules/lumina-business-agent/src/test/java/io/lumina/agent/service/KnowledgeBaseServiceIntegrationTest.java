package io.lumina.agent.service;

import io.lumina.agent.BaseIntegrationTest;
import io.lumina.agent.api.dto.KnowledgeBaseDTO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.KnowledgeBaseDO;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KnowledgeBaseService 集成测试
 *
 * <p>验证挂载关系表、可见性聚合、级联清理、租户隔离。
 *
 * @author Lumina Team
 * @since 3.1.0
 */
@Transactional
class KnowledgeBaseServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private AgentService agentService;

    private static final Long TENANT_A = 9201L;
    private static final Long TENANT_B = 9202L;
    private static final Long USER_ID = 9201L;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(TENANT_A);
        BaseContext.setUserId(USER_ID);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void createKnowledgeBaseSuccess() {
        KnowledgeBaseDTO dto = new KnowledgeBaseDTO();
        dto.setName("测试知识库-创建");
        dto.setDescription("集成测试用");
        dto.setVisibility("PRIVATE");

        KnowledgeBaseDO result = knowledgeBaseService.createKnowledgeBase(dto);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("测试知识库-创建");
        assertThat(result.getVisibility()).isEqualTo("PRIVATE");
        assertThat(result.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void mountKnowledgeBaseRelation() {
        KnowledgeBaseDO kb = knowledgeBaseService.createKnowledgeBase(buildDto("挂载测试KB", "PRIVATE"));
        Agent agent = agentService.createAgent(buildAgent("挂载测试Agent"));

        knowledgeBaseService.mountKnowledgeBase(agent.getAgentId(), kb.getId());

        List<Long> kbIds = knowledgeBaseService.getAgentKnowledgeBaseIds(agent.getAgentId());
        assertThat(kbIds).contains(kb.getId());

        // 解除挂载
        knowledgeBaseService.unmountKnowledgeBase(agent.getAgentId(), kb.getId());
        List<Long> afterUnmount = knowledgeBaseService.getAgentKnowledgeBaseIds(agent.getAgentId());
        assertThat(afterUnmount).doesNotContain(kb.getId());
    }

    @Test
    void getAccessibleKnowledgeBasesIncludesPublic() {
        // 创建 PRIVATE + PUBLIC 两个 KB
        KnowledgeBaseDO privateKb = knowledgeBaseService.createKnowledgeBase(buildDto("私有KB-可达", "PRIVATE"));
        KnowledgeBaseDO publicKb = knowledgeBaseService.createKnowledgeBase(buildDto("公共KB-可达", "PUBLIC"));
        Agent agent = agentService.createAgent(buildAgent("可达性测试Agent"));

        // 只挂载 PRIVATE 的
        knowledgeBaseService.mountKnowledgeBase(agent.getAgentId(), privateKb.getId());

        List<KnowledgeBaseDO> accessible = knowledgeBaseService.getAccessibleKnowledgeBases(agent.getAgentId());

        // 应包含挂载的 PRIVATE + 所有 PUBLIC
        assertThat(accessible).extracting(KnowledgeBaseDO::getId)
                .contains(privateKb.getId(), publicKb.getId());
    }

    @Test
    void deleteKnowledgeBaseCascadeClean() {
        KnowledgeBaseDO kb = knowledgeBaseService.createKnowledgeBase(buildDto("级联删除KB", "PRIVATE"));
        Agent agent = agentService.createAgent(buildAgent("级联删除Agent"));
        knowledgeBaseService.mountKnowledgeBase(agent.getAgentId(), kb.getId());

        // 删除 KB 前挂载关系存在
        assertThat(knowledgeBaseService.getAgentKnowledgeBaseIds(agent.getAgentId())).contains(kb.getId());

        knowledgeBaseService.deleteKnowledgeBase(kb.getId());

        // 删除后挂载关系应级联清理
        assertThat(knowledgeBaseService.getAgentKnowledgeBaseIds(agent.getAgentId()))
                .doesNotContain(kb.getId());
    }

    @Test
    void crossTenantIsolation() {
        BaseContext.setTenantId(TENANT_A);
        KnowledgeBaseDO kbA = knowledgeBaseService.createKnowledgeBase(buildDto("租户A的KB", "PRIVATE"));

        // 切到租户 B，查询应看不到租户 A 的 KB
        BaseContext.setTenantId(TENANT_B);
        assertThatThrownBy(() -> knowledgeBaseService.getKnowledgeBase(kbA.getId()))
                .isInstanceOf(BusinessException.class);
    }

    private KnowledgeBaseDTO buildDto(String name, String visibility) {
        KnowledgeBaseDTO dto = new KnowledgeBaseDTO();
        dto.setName(name);
        dto.setVisibility(visibility);
        return dto;
    }

    private Agent buildAgent(String name) {
        Agent agent = new Agent();
        agent.setAgentName(name);
        agent.setAgentType("ReAct");
        return agent;
    }
}
