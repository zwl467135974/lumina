package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.AgentTraceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 推理链追踪 Mapper
 *
 * @author Lumina Team
 * @since 3.7.0
 */
@Mapper
public interface AgentTraceMapper extends BaseMapper<AgentTraceDO> {
}
