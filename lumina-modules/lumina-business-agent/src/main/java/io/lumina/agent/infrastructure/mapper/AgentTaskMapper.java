package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 异步任务 Mapper
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTaskDO> {
}
