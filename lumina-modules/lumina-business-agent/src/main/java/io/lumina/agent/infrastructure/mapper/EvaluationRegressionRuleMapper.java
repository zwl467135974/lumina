package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.EvaluationRegressionRuleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评估回归规则 Mapper
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Mapper
public interface EvaluationRegressionRuleMapper extends BaseMapper<EvaluationRegressionRuleDO> {
}
