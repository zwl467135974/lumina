package io.lumina.agent.service;

import io.lumina.agent.api.vo.AgentTemplateVO;
import io.lumina.agent.domain.model.Agent;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 模板与分享中心服务（角色包 = Agent 模板 + 技能集合）
 *
 * <p>统一分享格式（zip）：manifest.json + agent.json + skills/{name}/SKILL.md。
 * 导出自动剥离 LLM 密钥；导入全走安全体检管线。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
public interface AgentTemplateService {

    /**
     * 导出角色包 zip（Agent 定义 + 可选随包技能）
     *
     * @param agentId       Agent ID
     * @param includeSkills 是否携带当前租户全部启用技能
     */
    byte[] exportBundle(Long agentId, boolean includeSkills);

    /**
     * 导入角色包 zip：技能走体检入库 → 模板入库（重名版本自增）
     *
     * @return 入库模板
     */
    AgentTemplateVO importBundle(MultipartFile file);

    /** 模板分页列表（当前租户，name 模糊过滤） */
    List<AgentTemplateVO> list(String name);

    /**
     * 从模板实例化 Agent（名称默认取模板名，冲突自动加序号后缀）
     */
    Agent instantiate(Long templateId, String nameOverride);

    /** 删除模板（逻辑删除，不影响已实例化的 Agent 与技能） */
    void delete(Long id);
}
