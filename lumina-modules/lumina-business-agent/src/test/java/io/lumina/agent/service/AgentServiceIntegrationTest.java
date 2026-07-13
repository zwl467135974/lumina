package io.lumina.agent.service;

import io.lumina.agent.BaseIntegrationTest;
import io.lumina.agent.domain.model.Agent;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentService 集成测试（仅 CRUD，排除 execute* 方法）
 *
 * <p>验证 DO↔Domain 映射、部分更新语义、租户隔离。
 *
 * @author Lumina Team
 * @since 3.1.0
 */
@Transactional
class AgentServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AgentService agentService;

    private static final Long TENANT_A = 9101L;
    private static final Long TENANT_B = 9102L;
    private static final Long USER_ID = 9101L;

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
    void createAgentPersistsFields() {
        Agent agent = buildAgent("测试Agent-创建", "ReAct");
        agent.setLlmConfig("{\"provider\":\"openai\",\"model\":\"gpt-4\"}");
        agent.setTools("search,calc");

        Agent result = agentService.createAgent(agent);

        assertThat(result.getAgentId()).isNotNull();

        // 从 DB 读回，验证 DO↔Domain 映射
        Agent reloaded = agentService.getAgentById(result.getAgentId());
        assertThat(reloaded.getAgentName()).isEqualTo("测试Agent-创建");
        assertThat(reloaded.getAgentType()).isEqualTo("ReAct");
        assertThat(reloaded.getLlmConfig()).contains("openai");
        assertThat(reloaded.getTools()).isEqualTo("search,calc");
        assertThat(reloaded.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void updateAgentPartialUpdate() {
        Agent created = agentService.createAgent(buildAgent("测试Agent-更新", "ReAct"));

        // 仅更新描述（部分更新，其余字段为 null 应跳过）
        Agent patch = new Agent();
        patch.setDescription("更新后的描述");
        Agent updated = agentService.updateAgent(created.getAgentId(), patch);

        assertThat(updated.getDescription()).isEqualTo("更新后的描述");
        // 原字段不变
        assertThat(updated.getAgentName()).isEqualTo("测试Agent-更新");
        assertThat(updated.getAgentType()).isEqualTo("ReAct");
    }

    @Test
    void deleteAgentCrossTenantThrows() {
        BaseContext.setTenantId(TENANT_A);
        Agent created = agentService.createAgent(buildAgent("租户A的Agent", "ReAct"));

        // 切换到租户 B，删除租户 A 的 Agent 应失败
        BaseContext.setTenantId(TENANT_B);
        assertThatThrownBy(() -> agentService.deleteAgent(created.getAgentId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getAgentByIdCrossTenantHidden() {
        BaseContext.setTenantId(TENANT_A);
        Agent created = agentService.createAgent(buildAgent("租户A专属", "Tool"));

        // 切换到租户 B，查询租户 A 的 Agent 应抛 NOT_FOUND（不可见）
        BaseContext.setTenantId(TENANT_B);
        assertThatThrownBy(() -> agentService.getAgentById(created.getAgentId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void pageAgentsFilterByType() {
        agentService.createAgent(buildAgent("类型测试-1", "ReAct"));
        agentService.createAgent(buildAgent("类型测试-2", "ReAct"));
        agentService.createAgent(buildAgent("类型测试-3", "Tool"));

        PageResult<Agent> reActPage = agentService.pageAgents(null, "ReAct", 1, 10);
        PageResult<Agent> toolPage = agentService.pageAgents(null, "Tool", 1, 10);

        // 注意：DB 可能有其它测试遗留的同类型数据，用 >= 断言
        assertThat(reActPage.getList().size()).isGreaterThanOrEqualTo(2);
        assertThat(toolPage.getList().size()).isGreaterThanOrEqualTo(1);
        assertThat(reActPage.getList()).allSatisfy(a ->
                assertThat(a.getAgentType()).isEqualTo("ReAct"));
    }

    private Agent buildAgent(String name, String type) {
        Agent agent = new Agent();
        agent.setAgentName(name);
        agent.setAgentType(type);
        agent.setDescription("集成测试用 Agent");
        return agent;
    }
}
