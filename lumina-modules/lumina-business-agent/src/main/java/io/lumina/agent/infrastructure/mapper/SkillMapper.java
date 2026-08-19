package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.SkillDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 技能 Mapper
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Mapper
public interface SkillMapper extends BaseMapper<SkillDO> {
}
