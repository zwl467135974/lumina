package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.BudgetRuleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预算管理规则 Mapper
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Mapper
public interface BudgetRuleMapper extends BaseMapper<BudgetRuleDO> {
}
