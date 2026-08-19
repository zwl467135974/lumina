package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.ToolArtifactDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具执行结果存档 Mapper
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Mapper
public interface ToolArtifactMapper extends BaseMapper<ToolArtifactDO> {
}
