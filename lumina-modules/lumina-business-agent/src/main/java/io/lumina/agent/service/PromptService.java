package io.lumina.agent.service;

import io.lumina.agent.api.dto.PromptDTO;
import io.lumina.agent.infrastructure.entity.PromptDO;

import java.util.List;

/**
 * Prompt 版本管理服务
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface PromptService {

    /** 创建 Prompt（v1 草稿） */
    PromptDO create(PromptDTO dto);

    /** 更新草稿版本 */
    PromptDO update(Long id, PromptDTO dto);

    /** 发布版本（设为激活） */
    PromptDO publish(Long id);

    /** 创建新版本（基于已有 Prompt） */
    PromptDO newVersion(Long sourceId, PromptDTO dto);

    /** 删除（软删除） */
    void delete(Long id);

    /** 查询列表（每个 name 的激活版本） */
    List<PromptDO> list(String name, int pageNum, int pageSize);

    /** 查询某 name 的所有版本 */
    List<PromptDO> getVersions(String name);

    /** 获取激活版本 */
    PromptDO getActive(String name);

    /** 按 ID 获取 */
    PromptDO getById(Long id);

    /**
     * 填充模板变量
     *
     * @param template  模板内容（含 {变量名} 占位符）
     * @param variables 变量键值对
     * @return 填充后的文本
     */
    String fillTemplate(String template, java.util.Map<String, String> variables);
}
