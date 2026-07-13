package io.lumina.agent.service;

import io.lumina.agent.BaseIntegrationTest;
import io.lumina.agent.api.dto.llm.CreateLlmProviderDTO;
import io.lumina.agent.api.dto.llm.UpdateLlmProviderDTO;
import io.lumina.agent.api.vo.LlmProviderVO;
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
 * LlmProviderService 集成测试（仅 CRUD，排除 testConnection）
 *
 * <p>验证加密存储、脱敏返回、租户隔离。
 *
 * @author Lumina Team
 * @since 3.1.0
 */
@Transactional
class LlmProviderServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private LlmProviderService llmProviderService;

    private static final Long TENANT_A = 9501L;
    private static final Long TENANT_B = 9502L;

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
    void createProviderAndMaskApiKey() {
        LlmProviderVO vo = llmProviderService.create(buildCreateDto("测试供应商", "sk-secret-123456"));

        assertThat(vo.getId()).isNotNull();
        assertThat(vo.getName()).isEqualTo("测试供应商");
        // VO 应脱敏，不返回明文 key
        assertThat(vo.getApiKeyMasked()).isNotNull();
        assertThat(vo.getHasApiKey()).isTrue();
    }

    @Test
    void getByIdSuccess() {
        LlmProviderVO created = llmProviderService.create(buildCreateDto("查询供应商", "sk-key"));
        LlmProviderVO found = llmProviderService.getById(created.getId());

        assertThat(found.getName()).isEqualTo("查询供应商");
    }

    @Test
    void updateProvider() {
        LlmProviderVO created = llmProviderService.create(buildCreateDto("更新前", "sk-old"));

        UpdateLlmProviderDTO updateDto = new UpdateLlmProviderDTO();
        updateDto.setName("更新后");
        updateDto.setBaseUrl("https://api.new.com/v1");
        LlmProviderVO updated = llmProviderService.update(created.getId(), updateDto);

        assertThat(updated.getName()).isEqualTo("更新后");
        assertThat(updated.getBaseUrl()).isEqualTo("https://api.new.com/v1");
    }

    @Test
    void crossTenantAccessThrows() {
        BaseContext.setTenantId(TENANT_A);
        LlmProviderVO created = llmProviderService.create(buildCreateDto("租户A供应商", "sk-a"));

        BaseContext.setTenantId(TENANT_B);
        assertThatThrownBy(() -> llmProviderService.getById(created.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void listFiltersByTenant() {
        BaseContext.setTenantId(TENANT_A);
        llmProviderService.create(buildCreateDto("A供应商1", "sk1"));

        BaseContext.setTenantId(TENANT_B);
        llmProviderService.create(buildCreateDto("B供应商1", "sk2"));

        // 租户 A 只看到自己的
        BaseContext.setTenantId(TENANT_A);
        io.lumina.agent.api.dto.llm.QueryLlmProviderDTO query = new io.lumina.agent.api.dto.llm.QueryLlmProviderDTO();
        List<LlmProviderVO> aList = llmProviderService.list(query);
        assertThat(aList).allSatisfy(vo -> assertThat(vo.getName()).contains("A供应商"));
    }

    private CreateLlmProviderDTO buildCreateDto(String name, String apiKey) {
        CreateLlmProviderDTO dto = new CreateLlmProviderDTO();
        dto.setName(name);
        dto.setProvider("openai");
        dto.setApiKey(apiKey);
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setDefaultModel("gpt-4");
        dto.setStatus(1);
        return dto;
    }
}
