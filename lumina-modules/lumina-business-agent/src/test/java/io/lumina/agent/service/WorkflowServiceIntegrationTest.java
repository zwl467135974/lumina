package io.lumina.agent.service;

import io.lumina.agent.BaseIntegrationTest;
import io.lumina.agent.api.dto.WorkflowDTO;
import io.lumina.agent.infrastructure.entity.WorkflowDefinitionDO;
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
 * WorkflowService 集成测试（仅 definition CRUD，排除 execute*）
 *
 * <p>验证工作流定义创建、发布、查询、租户隔离。
 *
 * @author Lumina Team
 * @since 3.1.0
 */
@Transactional
class WorkflowServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WorkflowService workflowService;

    private static final Long TENANT_A = 9701L;
    private static final Long TENANT_B = 9702L;

    private static final String VALID_YAML = """
            name: "test-workflow"
            description: "集成测试工作流"
            inputs:
              - name: query
                type: string
                required: true
            nodes:
              - id: step1
                type: agent
                agentId: 1
                input: "$.query"
                outputVar: "result"
            """;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(TENANT_A);
        BaseContext.setUserId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void createWorkflowSuccess() {
        WorkflowDefinitionDO def = workflowService.create(buildDto("测试工作流-创建"));

        assertThat(def.getId()).isNotNull();
        assertThat(def.getName()).isEqualTo("测试工作流-创建");
        assertThat(def.getStatus()).isEqualTo(0); // 草稿
        assertThat(def.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void getByIdSuccess() {
        WorkflowDefinitionDO created = workflowService.create(buildDto("查询工作流"));
        WorkflowDefinitionDO found = workflowService.getById(created.getId());

        assertThat(found.getName()).isEqualTo("查询工作流");
    }

    @Test
    void publishWorkflow() {
        WorkflowDefinitionDO created = workflowService.create(buildDto("发布工作流"));
        assertThat(created.getStatus()).isEqualTo(0); // 草稿

        workflowService.publish(created.getId());

        WorkflowDefinitionDO published = workflowService.getById(created.getId());
        assertThat(published.getStatus()).isEqualTo(1); // 已发布
    }

    @Test
    void crossTenantGetThrows() {
        BaseContext.setTenantId(TENANT_A);
        WorkflowDefinitionDO created = workflowService.create(buildDto("租户A工作流"));

        BaseContext.setTenantId(TENANT_B);
        assertThatThrownBy(() -> workflowService.getById(created.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void listFiltersByTenant() {
        BaseContext.setTenantId(TENANT_A);
        workflowService.create(buildDto("A工作流"));

        BaseContext.setTenantId(TENANT_B);
        workflowService.create(buildDto("B工作流"));

        BaseContext.setTenantId(TENANT_A);
        PageResult<WorkflowDefinitionDO> list = workflowService.list(null, null, 1, 10);
        assertThat(list.getList()).allSatisfy(def ->
                assertThat(def.getTenantId()).isEqualTo(TENANT_A));
    }

    private WorkflowDTO buildDto(String name) {
        WorkflowDTO dto = new WorkflowDTO();
        dto.setName(name);
        dto.setDescription("集成测试工作流");
        dto.setDefinitionYaml(VALID_YAML);
        return dto;
    }
}
