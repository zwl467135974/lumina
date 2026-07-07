package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.AgentKnowledgeBaseDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent-知识库挂载 Mapper
 *
 * @author Lumina Team
 * @since 2.1.0
 */
@Mapper
public interface AgentKnowledgeBaseMapper extends BaseMapper<AgentKnowledgeBaseDO> {
}
