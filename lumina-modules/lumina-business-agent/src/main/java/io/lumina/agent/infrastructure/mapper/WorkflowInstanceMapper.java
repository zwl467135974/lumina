package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.WorkflowInstanceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流实例 Mapper
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Mapper
public interface WorkflowInstanceMapper extends BaseMapper<WorkflowInstanceDO> {
}
