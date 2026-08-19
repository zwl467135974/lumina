package io.lumina.agent.service;

import io.lumina.agent.api.dto.SkillDTO;
import io.lumina.agent.api.vo.SkillVO;

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
}
