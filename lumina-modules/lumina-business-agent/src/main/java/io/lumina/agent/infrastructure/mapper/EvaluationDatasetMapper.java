package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.EvaluationDatasetDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评估数据集 Mapper
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Mapper
public interface EvaluationDatasetMapper extends BaseMapper<EvaluationDatasetDO> {
}
