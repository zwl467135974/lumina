package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.WorkflowExecutionLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流执行日志 Mapper
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Mapper
public interface WorkflowExecutionLogMapper extends BaseMapper<WorkflowExecutionLogDO> {
}
