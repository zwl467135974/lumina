package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.EvaluationRunDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评估运行记录 Mapper
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Mapper
public interface EvaluationRunMapper extends BaseMapper<EvaluationRunDO> {
}
