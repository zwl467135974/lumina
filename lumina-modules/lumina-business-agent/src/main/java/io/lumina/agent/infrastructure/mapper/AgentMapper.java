package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.AgentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent Mapper
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentDO> {
}
