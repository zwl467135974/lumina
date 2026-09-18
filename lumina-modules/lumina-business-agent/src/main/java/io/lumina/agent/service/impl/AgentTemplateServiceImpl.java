package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.lumina.agent.api.vo.AgentTemplateVO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentDO;
import io.lumina.agent.infrastructure.entity.AgentTemplateDO;
import io.lumina.agent.infrastructure.entity.SkillDO;
import io.lumina.agent.infrastructure.mapper.AgentMapper;
import io.lumina.agent.infrastructure.mapper.AgentTemplateMapper;
import io.lumina.agent.infrastructure.mapper.SkillMapper;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.AgentTemplateService;
import io.lumina.agent.service.SkillService;
import io.lumina.agent.service.support.SkillMarkdownParser;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 模板与分享中心服务实现
 *
 * <p>角色包 zip 结构：manifest.json + agent.json + skills/{name}/SKILL.md。
 * 导出剥离 llm.apiKey（密钥永不离开本租户）；导入技能逐条走体检
 * （拒收不阻断整包，明细记日志），模板重名时版本自增。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTemplateServiceImpl implements AgentTemplateService {

    /** 导入 zip 解压总量上限 */
    private static final long MAX_BUNDLE_BYTES = 20L * 1024 * 1024;

    private static final String MANIFEST = "manifest.json";
    private static final String AGENT_JSON = "agent.json";

    private final AgentTemplateMapper templateMapper;
    private final AgentMapper agentMapper;
    private final SkillMapper skillMapper;
    private final AgentService agentService;
    private final SkillService skillService;
    private final ObjectMapper objectMapper;

    // ==================== 导出 ====================

    @Override
    public byte[] exportBundle(Long agentId, boolean includeSkills) {
        Agent agent = agentService.getAgentById(agentId);
        List<SkillDO> skills = includeSkills ? enabledSkills() : List.of();

        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(buffer)) {
            // agent.json（剥离密钥）
            ObjectNode agentJson = objectMapper.createObjectNode();
            agentJson.put("name", agent.getAgentName());
            agentJson.put("agentType", agent.getAgentType());
            agentJson.put("description", agent.getDescription() != null ? agent.getDescription() : "");
            if (agent.getTools() != null && !agent.getTools().isBlank()) {
                com.fasterxml.jackson.databind.node.ArrayNode toolsArray = agentJson.putArray("tools");
                java.util.Arrays.stream(agent.getTools().split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .forEach(toolsArray::add);
            }
            if (agent.getSubAgents() != null && !agent.getSubAgents().isBlank()) {
                agentJson.put("subAgents", agent.getSubAgents());
            }
            JsonNode llm = sanitizeLlm(agent.getLlmConfig());
            if (llm != null && llm.size() > 0) {
                agentJson.set("llm", llm);
            }
            List<String> skillNames = skills.stream().map(SkillDO::getName).toList();
            agentJson.set("skills", objectMapper.valueToTree(skillNames));

            zip.putNextEntry(new ZipEntry(AGENT_JSON));
            zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(agentJson));
            zip.closeEntry();

            // manifest.json
            ObjectNode manifest = objectMapper.createObjectNode();
            manifest.put("format", "lumina-bundle");
            manifest.put("version", 1);
            manifest.put("exportedAt", LocalDateTime.now().toString());
            manifest.put("skills", skillNames.size());
            zip.putNextEntry(new ZipEntry(MANIFEST));
            zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            zip.closeEntry();

            // skills/
            for (SkillDO skill : skills) {
                zip.putNextEntry(new ZipEntry("skills/" + skill.getName() + "/SKILL.md"));
                zip.write(SkillMarkdownParser.toMarkdown(
                                skill.getName(), skill.getDescription(), skill.getWhenToUse(), skill.getContent())
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            log.info("导出角色包: agent={}, skills={}", agent.getAgentName(), skillNames.size());
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "角色包导出失败: " + e.getMessage());
        }
    }

    /** llmConfig JSON 去密钥（apiKey 及任何含 key/secret 的字段） */
    private JsonNode sanitizeLlm(String llmConfigJson) {
        if (llmConfigJson == null || llmConfigJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(llmConfigJson);
            if (node.isObject()) {
                ObjectNode obj = (ObjectNode) node;
                obj.fieldNames().forEachRemaining(field -> {
                    if (field.toLowerCase().contains("key") || field.toLowerCase().contains("secret")) {
                        obj.remove(field);
                    }
                });
            }
            return node;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 导入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentTemplateVO importBundle(MultipartFile file) {
        BundleContent bundle = readBundle(file);

        // 技能逐条走体检管线（拒收/重名记录日志，不阻断整包）
        List<String> importedSkills = new ArrayList<>();
        bundle.skills.forEach((folder, markdown) -> {
            try {
                SkillMarkdownParser.ParsedSkill parsed = SkillMarkdownParser.parse(markdown);
                skillService.importSkillContent(parsed.name(), parsed.description(),
                        parsed.whenToUse(), parsed.content(), List.of());
                importedSkills.add(parsed.name());
            } catch (Exception e) {
                log.warn("角色包技能导入跳过: {}, {}", folder, e.getMessage());
            }
        });

        JsonNode agentJson = bundle.agentJson;
        String name = agentJson.path("name").asText("").trim();
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "agent.json 缺少 name");
        }
        AgentTemplateDO template = new AgentTemplateDO();
        template.setName(name);
        template.setAgentType(agentJson.path("agentType").asText("REACT"));
        template.setDescription(agentJson.path("description").asText(null));
        template.setContent(bundle.agentJson.toString());
        template.setSkillNames(String.join(",", importedSkills));
        template.setSource("IMPORT");
        template.setTenantId(currentTenant());
        template.setCreateBy(BaseContext.getUserId());
        template.setIsDeleted(0);
        template.setVersion(1);
        // 同名模板版本自增
        AgentTemplateDO existing = templateMapper.selectOne(new LambdaQueryWrapper<AgentTemplateDO>()
                .eq(AgentTemplateDO::getTenantId, currentTenant())
                .eq(AgentTemplateDO::getName, name)
                .eq(AgentTemplateDO::getIsDeleted, 0)
                .orderByDesc(AgentTemplateDO::getVersion)
                .last("LIMIT 1"));
        if (existing != null) {
            template.setVersion(existing.getVersion() + 1);
        }
        templateMapper.insert(template);
        log.info("角色包导入: template={}, v{}, skills={}/{}",
                name, template.getVersion(), importedSkills.size(), bundle.skills.size());
        return AgentTemplateVO.from(template);
    }

    private record BundleContent(JsonNode agentJson, java.util.Map<String, String> skills) {
    }

    private BundleContent readBundle(MultipartFile file) {
        JsonNode agentJson = null;
        java.util.Map<String, String> skills = new java.util.LinkedHashMap<>();
        long total = 0;
        try (InputStream in = file.getInputStream(); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.contains("..")) {
                    continue;
                }
                byte[] bytes = zip.readAllBytes();
                total += bytes.length;
                if (total > MAX_BUNDLE_BYTES) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "角色包解压总量超过上限");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                if (AGENT_JSON.equals(name)) {
                    agentJson = objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
                } else if (name.startsWith("skills/") && name.endsWith(".md")) {
                    String folder = name.substring("skills/".length()).split("/")[0];
                    skills.merge(folder, new String(bytes, StandardCharsets.UTF_8),
                            (a, b) -> a + "\n\n" + b);
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色包读取失败: " + e.getMessage());
        }
        if (agentJson == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色包缺少 agent.json");
        }
        return new BundleContent(agentJson, skills);
    }

    // ==================== 列表 / 实例化 / 删除 ====================

    @Override
    public List<AgentTemplateVO> list(String name) {
        LambdaQueryWrapper<AgentTemplateDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTemplateDO::getTenantId, currentTenant());
        wrapper.eq(AgentTemplateDO::getIsDeleted, 0);
        if (name != null && !name.isBlank()) {
            wrapper.like(AgentTemplateDO::getName, name.trim());
        }
        wrapper.orderByDesc(AgentTemplateDO::getUpdateTime);
        return templateMapper.selectList(wrapper).stream().map(AgentTemplateVO::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Agent instantiate(Long templateId, String nameOverride) {
        AgentTemplateDO template = requireOwned(templateId);
        JsonNode agentJson;
        try {
            agentJson = objectMapper.readTree(template.getContent());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板内容损坏，无法实例化");
        }

        Agent agent = new Agent();
        agent.setAgentName(uniqueAgentName(
                nameOverride != null && !nameOverride.isBlank() ? nameOverride.trim() : template.getName()));
        agent.setAgentType(agentJson.path("agentType").asText("REACT"));
        agent.setDescription(agentJson.path("description").asText(null));
        JsonNode tools = agentJson.path("tools");
        if (tools.isArray() && tools.size() > 0) {
            List<String> toolList = new ArrayList<>();
            tools.forEach(t -> toolList.add(t.asText()));
            agent.setTools(String.join(",", toolList));
        }
        if (agentJson.hasNonNull("subAgents")) {
            agent.setSubAgents(agentJson.get("subAgents").asText());
        }
        JsonNode llm = agentJson.path("llm");
        if (llm.isObject() && llm.size() > 0) {
            agent.setLlmConfig(llm.toString());
        }
        agent.setStatus(1);
        Agent created = agentService.createAgent(agent);
        log.info("模板实例化: template={} -> agentId={}, name={}",
                template.getName(), created.getAgentId(), created.getAgentName());
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireOwned(id);
        templateMapper.deleteById(id);
    }

    // ==================== 私有方法 ====================

    private AgentTemplateDO requireOwned(Long id) {
        AgentTemplateDO template = templateMapper.selectById(id);
        if (template == null || template.getIsDeleted() != 0
                || !template.getTenantId().equals(currentTenant())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        return template;
    }

    private List<SkillDO> enabledSkills() {
        return skillMapper.selectList(new LambdaQueryWrapper<SkillDO>()
                .eq(SkillDO::getTenantId, currentTenant())
                .eq(SkillDO::getIsDeleted, 0)
                .eq(SkillDO::getEnabled, 1)
                .orderByAsc(SkillDO::getName));
    }

    private String uniqueAgentName(String base) {
        Long tenantId = currentTenant();
        String candidate = base.length() > 90 ? base.substring(0, 90) : base;
        int suffix = 0;
        while (agentMapper.selectCount(new LambdaQueryWrapper<AgentDO>()
                .eq(AgentDO::getAgentName, candidate)
                .eq(AgentDO::getTenantId, tenantId)) > 0) {
            suffix++;
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private Long currentTenant() {
        return BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
    }
}
