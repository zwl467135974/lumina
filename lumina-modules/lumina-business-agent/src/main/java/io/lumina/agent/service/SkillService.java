package io.lumina.agent.service;

import io.lumina.agent.api.dto.SkillDTO;
import io.lumina.agent.api.dto.SkillImportResult;
import io.lumina.agent.api.vo.SkillVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 技能管理服务
 *
 * @author Lumina Team
 * @since 3.11.0
 */
public interface SkillService {

    /** 创建技能（租户内名称唯一） */
    SkillVO create(SkillDTO dto);

    /** 更新技能 */
    SkillVO update(Long id, SkillDTO dto);

    /** 启用/禁用（禁用后不进目录、不可加载） */
    SkillVO setEnabled(Long id, boolean enabled);

    /** 删除（逻辑删除） */
    void delete(Long id);

    /** 分页列表（当前租户） */
    List<SkillVO> list(String name, int pageNum, int pageSize);

    /**
     * 导入 SKILL.md（单个 .md 或多技能 .zip，开放标准格式）
     *
     * <p>导入前强制安全体检：HIGH（注入/破坏性指令）拒收；MEDIUM（可疑）落库但禁用待复核。
     *
     * @since 3.12.0
     */
    SkillImportResult importSkills(MultipartFile file);

    /** 导出为 SKILL.md 文本（单个技能，开放标准格式） @since 3.12.0 */
    String exportMarkdown(Long id);

    /** 导出当前租户全部技能为 zip（每技能一个 {name}/SKILL.md 目录） @since 3.12.0 */
    byte[] exportAllAsZip();

    /** 对已入库技能重跑安全体检（REJECTED 将强制禁用） @since 3.12.0 */
    SkillVO rescan(Long id);

    /**
     * 导入单条技能内容（角色包导入复用；与文件导入同一体检/查重管线）
     *
     * @since 3.12.0
     */
    SkillImportResult.ImportedSkill importSkillContent(String name, String description,
                                                        String whenToUse, String content,
                                                        List<String> bundledFiles);

    /**
     * 从 URL/Git 仓库导入技能：
     * 任意 http(s) 单文件（.md）/ GitHub 仓库或子目录（api 遍历找 SKILL.md）/ Gitee 同构
     *
     * @since 3.12.0
     */
    SkillImportResult importFromUrl(String url);
}
