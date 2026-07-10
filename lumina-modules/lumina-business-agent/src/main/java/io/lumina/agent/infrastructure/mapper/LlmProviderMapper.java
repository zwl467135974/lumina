package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.LlmProviderDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * LLM 供应商配置 Mapper
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Mapper
public interface LlmProviderMapper extends BaseMapper<LlmProviderDO> {
}
