package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.AgentTemplateDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 模板 Mapper
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Mapper
public interface AgentTemplateMapper extends BaseMapper<AgentTemplateDO> {
}
