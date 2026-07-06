package io.lumina.agent.service;

import io.lumina.agent.api.dto.BudgetRuleDTO;
import io.lumina.agent.infrastructure.entity.BudgetRuleDO;

import java.util.List;
import java.util.Map;

/**
 * 预算管理服务
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface BudgetService {

    /**
     * 预算检查（Agent 执行前调用）
     *
     * @param agentId Agent ID
     * @throws io.lumina.common.exception.BusinessException 预算超限时抛出 {@link io.lumina.common.core.ErrorCode#BUDGET_EXCEEDED}
     */
    void checkBudget(Long agentId);

    /** 创建预算规则 */
    BudgetRuleDO createRule(BudgetRuleDTO dto);

    /** 删除预算规则 */
    void deleteRule(Long id);

    /** 查询预算规则列表 */
    List<BudgetRuleDO> listRules();

    /** 查询预算使用情况（含当前消费、上限、告警阈值） */
    List<Map<String, Object>> getUsage();
}
