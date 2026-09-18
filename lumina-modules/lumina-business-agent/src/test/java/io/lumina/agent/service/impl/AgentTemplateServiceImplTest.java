package io.lumina.agent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentDO;
import io.lumina.agent.infrastructure.entity.AgentTemplateDO;
import io.lumina.agent.infrastructure.entity.SkillDO;
import io.lumina.agent.infrastructure.mapper.AgentMapper;
import io.lumina.agent.infrastructure.mapper.AgentTemplateMapper;
import io.lumina.agent.infrastructure.mapper.SkillMapper;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.SkillService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentTemplateServiceImpl 单元测试（密钥剥离、角色包 round-trip、版本自增、实例化去重）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
class AgentTemplateServiceImplTest {

    private final AgentTemplateMapper templateMapper = Mockito.mock(AgentTemplateMapper.class);
    private final AgentMapper agentMapper = Mockito.mock(AgentMapper.class);
    private final SkillMapper skillMapper = Mockito.mock(SkillMapper.class);
    private final AgentService agentService = Mockito.mock(AgentService.class);
    private final SkillService skillService = Mockito.mock(SkillService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgentTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentTemplateServiceImpl(templateMapper, agentMapper, skillMapper,
                agentService, skillService, objectMapper);
        BaseContext.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void exportBundleStripsApiKeyAndIncludesSkills() throws Exception {
        when(agentService.getAgentById(3L)).thenReturn(agent());
        when(skillMapper.selectList(any())).thenReturn(List.of(skill("e2e-a"), skill("e2e-b")));

        byte[] zipBytes = service.exportBundle(3L, true);

        Map<String, String> entries = unzip(zipBytes);
        assertThat(entries).containsKeys("manifest.json", "agent.json",
                "skills/e2e-a/SKILL.md", "skills/e2e-b/SKILL.md");
        JsonNode agentJson = objectMapper.readTree(entries.get("agent.json"));
        assertThat(agentJson.path("name").asText()).isEqualTo("分享助手");
        assertThat(agentJson.path("tools").toString()).contains("util.getCurrentTime");
        assertThat(entries.get("agent.json")).doesNotContain("sk-secret-should-never-leave");
        assertThat(agentJson.path("llm").path("modelType").asText()).isEqualTo("glm");
    }

    @Test
    void exportWithoutSkillsContainsNoSkillEntries() throws Exception {
        when(agentService.getAgentById(3L)).thenReturn(agent());

        Map<String, String> entries = unzip(service.exportBundle(3L, false));

        assertThat(entries).containsKeys("manifest.json", "agent.json");
        assertThat(entries.keySet()).noneMatch(k -> k.startsWith("skills/"));
    }

    @Test
    void importBundleImportsSkillsAndTemplate() throws Exception {
        when(agentService.getAgentById(3L)).thenReturn(agent());
        when(skillMapper.selectList(any())).thenReturn(List.of(skill("e2e-a")));
        byte[] zipBytes = service.exportBundle(3L, true);

        MockMultipartFile file = new MockMultipartFile("file", "bundle.zip",
                "application/zip", zipBytes);
        when(templateMapper.selectOne(any())).thenReturn(null);
        when(templateMapper.insert(any(AgentTemplateDO.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentTemplateDO.class).setId(1L);
            return 1;
        });

        var vo = service.importBundle(file);

        assertThat(vo.getName()).isEqualTo("分享助手");
        assertThat(vo.getSkillNames()).containsExactly("e2e-a");
        verify(skillService).importSkillContent(eq("e2e-a"), anyString(), any(), anyString(), any());
        verify(templateMapper).insert(Mockito.<AgentTemplateDO>argThat(t ->
                "IMPORT".equals(t.getSource()) && t.getVersion() == 1
                        && t.getContent().contains("分享助手")));
    }

    @Test
    void importBundleIncrementsVersionOnDuplicateName() throws Exception {
        when(agentService.getAgentById(3L)).thenReturn(agent());
        when(skillMapper.selectList(any())).thenReturn(List.of());
        byte[] zipBytes = service.exportBundle(3L, false);

        AgentTemplateDO existing = new AgentTemplateDO();
        existing.setName("分享助手");
        existing.setVersion(1);
        when(templateMapper.selectOne(any())).thenReturn(existing);
        when(templateMapper.insert(any(AgentTemplateDO.class))).thenAnswer(inv -> {
            inv.getArgument(0, AgentTemplateDO.class).setId(2L);
            return 1;
        });

        var vo = service.importBundle(new MockMultipartFile("file", "b.zip", "application/zip", zipBytes));

        assertThat(vo.getVersion()).isEqualTo(2);
    }

    @Test
    void instantiateResolvesNameConflictAndDropsSecrets() {
        AgentTemplateDO template = new AgentTemplateDO();
        template.setId(9L);
        template.setName("分享助手");
        template.setTenantId(7L);
        template.setIsDeleted(0);
        template.setContent("{\"name\":\"分享助手\",\"agentType\":\"REACT\",\"tools\":[\"util.getCurrentTime\"],"
                + "\"llm\":{\"modelType\":\"glm\",\"modelName\":\"glm-4-flash\"}}");
        when(templateMapper.selectById(9L)).thenReturn(template);
        // 同名已存在 → 唯一化应产生 分享助手-1
        when(agentMapper.selectCount(any())).thenReturn(1L, 0L);
        when(agentService.createAgent(any())).thenAnswer(inv -> inv.getArgument(0, Agent.class));

        Agent created = service.instantiate(9L, null);

        assertThat(created.getAgentName()).isEqualTo("分享助手-1");
        assertThat(created.getTools()).isEqualTo("util.getCurrentTime");
        assertThat(created.getLlmConfig()).contains("glm-4-flash");
    }

    @Test
    void importBundleWithoutAgentJsonRejected() {
        byte[] zipWithoutAgent = zipOf(Map.of("manifest.json", "{\"format\":\"lumina-bundle\"}"));
        MockMultipartFile file = new MockMultipartFile("file", "b.zip", "application/zip", zipWithoutAgent);

        assertThatThrownBy(() -> service.importBundle(file))
                .hasMessageContaining("agent.json");
        verify(templateMapper, never()).insert(Mockito.<AgentTemplateDO>any());
    }

    // ==================== 辅助 ====================

    private Agent agent() {
        Agent agent = new Agent();
        agent.setAgentId(3L);
        agent.setAgentName("分享助手");
        agent.setAgentType("REACT");
        agent.setDescription("角色包测试");
        agent.setTools("util.getCurrentTime,util.loadSkill");
        agent.setLlmConfig("{\"modelType\":\"glm\",\"modelName\":\"glm-4-flash\",\"apiKey\":\"sk-secret-should-never-leave\"}");
        agent.setTenantId(7L);
        agent.setStatus(1);
        return agent;
    }

    private SkillDO skill(String name) {
        SkillDO skillDO = new SkillDO();
        skillDO.setName(name);
        skillDO.setDescription(name + " 技能");
        skillDO.setContent("## " + name);
        return skillDO;
    }

    private static Map<String, String> unzip(byte[] zipBytes) throws Exception {
        Map<String, String> entries = new java.util.LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return entries;
    }

    private static byte[] zipOf(Map<String, String> files) {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer)) {
            for (Map.Entry<String, String> e : files.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return buffer.toByteArray();
    }
}
