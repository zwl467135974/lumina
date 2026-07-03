package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.PromptDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Prompt Mapper
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Mapper
public interface PromptMapper extends BaseMapper<PromptDO> {
}
