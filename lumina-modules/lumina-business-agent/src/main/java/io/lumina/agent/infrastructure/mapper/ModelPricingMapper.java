package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.ModelPricingDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型价格 Mapper
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Mapper
public interface ModelPricingMapper extends BaseMapper<ModelPricingDO> {
}
