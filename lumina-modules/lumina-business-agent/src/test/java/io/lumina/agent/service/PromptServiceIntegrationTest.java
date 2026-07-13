package io.lumina.agent.service;

import io.lumina.agent.BaseIntegrationTest;
import io.lumina.agent.api.dto.PromptDTO;
import io.lumina.agent.infrastructure.entity.PromptDO;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PromptService 集成测试
 *
 * <p>验证版本管理事务完整性:创建/发布/新版本/更新约束/激活状态机。
 *
 * @author Lumina Team
 * @since 3.1.0
 */
@Transactional
class PromptServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PromptService promptService;

    private static final Long TEST_TENANT_ID = 9001L;
    private static final Long TEST_USER_ID = 9001L;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(TEST_TENANT_ID);
        BaseContext.setUserId(TEST_USER_ID);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void createPromptSuccess() {
        PromptDTO dto = buildDto("test-prompt-create", "你好，{name}");

        PromptDO result = promptService.create(dto);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("test-prompt-create");
        assertThat(result.getVersion()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(0);
        assertThat(result.getIsActive()).isEqualTo(0);
        assertThat(result.getTenantId()).isEqualTo(TEST_TENANT_ID);
    }

    @Test
    void publishPromptDeactivatesOldVersion() {
        // 创建并发布 v1
        PromptDO v1 = promptService.create(buildDto("test-prompt-publish", "v1 内容"));
        promptService.publish(v1.getId());

        // 确认 v1 激活
        PromptDO activeV1 = promptService.getActive("test-prompt-publish");
        assertThat(activeV1.getId()).isEqualTo(v1.getId());
        assertThat(activeV1.getStatus()).isEqualTo(1);
        assertThat(activeV1.getIsActive()).isEqualTo(1);

        // 创建并发布 v2
        PromptDO v2 = promptService.newVersion(v1.getId(), buildDto("test-prompt-publish", "v2 内容"));
        promptService.publish(v2.getId());

        // v1 应被取消激活
        PromptDO reloadedV1 = promptService.getById(v1.getId());
        assertThat(reloadedV1.getIsActive()).isEqualTo(0);

        // 激活版本切到 v2
        PromptDO activeV2 = promptService.getActive("test-prompt-publish");
        assertThat(activeV2.getId()).isEqualTo(v2.getId());
    }

    @Test
    void newVersionIncrementsVersionNumber() {
        PromptDO v1 = promptService.create(buildDto("test-prompt-version", "v1"));
        PromptDO v2 = promptService.newVersion(v1.getId(), buildDto("test-prompt-version", "v2"));
        PromptDO v3 = promptService.newVersion(v1.getId(), buildDto("test-prompt-version", "v3"));

        assertThat(v1.getVersion()).isEqualTo(1);
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v3.getVersion()).isEqualTo(3);
        assertThat(v3.getStatus()).isEqualTo(0);
        assertThat(v3.getIsActive()).isEqualTo(0);
    }

    @Test
    void updatePublishedPromptThrows() {
        PromptDO prompt = promptService.create(buildDto("test-prompt-update", "原始内容"));
        promptService.publish(prompt.getId());

        // 已发布版本不能修改
        PromptDTO updateDto = buildDto("test-prompt-update", "修改内容");
        assertThatThrownBy(() -> promptService.update(prompt.getId(), updateDto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateDraftPromptSuccess() {
        PromptDO prompt = promptService.create(buildDto("test-prompt-draft", "草稿内容"));
        assertThat(prompt.getStatus()).isEqualTo(0); // 草稿

        PromptDTO updateDto = buildDto("test-prompt-draft", "修改后的草稿");
        updateDto.setDescription("更新描述");
        PromptDO updated = promptService.update(prompt.getId(), updateDto);

        assertThat(updated.getContent()).isEqualTo("修改后的草稿");
    }

    @Test
    void duplicateNameThrows() {
        promptService.create(buildDto("test-prompt-dup", "内容"));

        // 同租户同名应拒绝
        assertThatThrownBy(() -> promptService.create(buildDto("test-prompt-dup", "另一个内容")))
                .isInstanceOf(BusinessException.class);
    }

    private PromptDTO buildDto(String name, String content) {
        PromptDTO dto = new PromptDTO();
        dto.setName(name);
        dto.setContent(content);
        dto.setAgentType("ReAct");
        return dto;
    }
}
