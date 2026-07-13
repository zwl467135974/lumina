package io.lumina.agent.service;

import io.lumina.agent.BaseIntegrationTest;
import io.lumina.agent.api.dto.EvaluationDatasetDTO;
import io.lumina.agent.evaluation.model.EvaluationDataset;
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
 * EvaluationService 集成测试（仅数据集 CRUD，排除 runEvaluation*）
 *
 * <p>验证数据集创建(YAML 解析)、查询、删除、租户隔离。
 *
 * @author Lumina Team
 * @since 3.1.0
 */
@Transactional
class EvaluationServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EvaluationService evaluationService;

    private static final Long TENANT_A = 9601L;
    private static final Long TENANT_B = 9602L;

    private static final String VALID_YAML = """
            - input: "你好"
              expected: "你好，有什么可以帮你的吗？"
            - input: "今天天气怎么样"
              expected: "今天天气晴朗"
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
    void createDatasetParsesYaml() {
        EvaluationDataset ds = evaluationService.createDataset(buildDto("测试数据集", VALID_YAML));

        assertThat(ds.getId()).isNotNull();
        assertThat(ds.getName()).isEqualTo("测试数据集");
        assertThat(ds.getCases()).hasSize(2);
    }

    @Test
    void getDatasetSuccess() {
        EvaluationDataset created = evaluationService.createDataset(buildDto("查询数据集", VALID_YAML));
        EvaluationDataset found = evaluationService.getDataset(created.getId());

        assertThat(found.getName()).isEqualTo("查询数据集");
    }

    @Test
    void listDatasetsReturnsCurrentTenant() {
        evaluationService.createDataset(buildDto("数据集A1", VALID_YAML));

        BaseContext.setTenantId(TENANT_B);
        evaluationService.createDataset(buildDto("数据集B1", VALID_YAML));

        BaseContext.setTenantId(TENANT_A);
        List<EvaluationDataset> list = evaluationService.listDatasets(null);
        assertThat(list).allSatisfy(ds -> assertThat(ds.getTenantId()).isEqualTo(TENANT_A));
    }

    @Test
    void deleteDatasetSuccess() {
        EvaluationDataset created = evaluationService.createDataset(buildDto("删除数据集", VALID_YAML));
        evaluationService.deleteDataset(created.getId());

        // 删除后查询应抛异常（软删除后不可见）
        assertThatThrownBy(() -> evaluationService.getDataset(created.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void crossTenantDeleteThrows() {
        BaseContext.setTenantId(TENANT_A);
        EvaluationDataset created = evaluationService.createDataset(buildDto("租户A数据集", VALID_YAML));

        BaseContext.setTenantId(TENANT_B);
        assertThatThrownBy(() -> evaluationService.deleteDataset(created.getId()))
                .isInstanceOf(BusinessException.class);
    }

    private EvaluationDatasetDTO buildDto(String name, String yaml) {
        EvaluationDatasetDTO dto = new EvaluationDatasetDTO();
        dto.setName(name);
        dto.setDescription("集成测试数据集");
        dto.setAgentType("ReAct");
        dto.setCasesYaml(yaml);
        return dto;
    }
}
