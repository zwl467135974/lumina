package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.lumina.agent.api.dto.BudgetRuleDTO;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.infrastructure.entity.BudgetRuleDO;
import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import io.lumina.agent.infrastructure.mapper.BudgetRuleMapper;
import io.lumina.agent.service.BudgetService;
import io.lumina.agent.service.CostService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预算管理服务实现
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements BudgetService {

    private final BudgetRuleMapper budgetRuleMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final CostService costService;

    private static final String SCOPE_TENANT = "TENANT";
    private static final String SCOPE_AGENT = "AGENT";
    private static final String SCOPE_USER = "USER";
    private static final String PERIOD_DAILY = "DAILY";
    private static final String PERIOD_MONTHLY = "MONTHLY";
    private static final String TASK_COMPLETED = "COMPLETED";

    @Override
    public void checkBudget(Long agentId) {
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        Long userId = BaseContext.getUserId();

        LambdaQueryWrapper<BudgetRuleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BudgetRuleDO::getTenantId, tenantId);
        wrapper.eq(BudgetRuleDO::getStatus, 1);
        wrapper.eq(BudgetRuleDO::getIsDeleted, 0);

        List<BudgetRuleDO> rules = budgetRuleMapper.selectList(wrapper);
        if (rules.isEmpty()) {
            return;
        }

        for (BudgetRuleDO rule : rules) {
            BigDecimal usage = calculateUsage(rule, tenantId, agentId, userId);
            BigDecimal limit = rule.getLimitAmount();

            if (usage.compareTo(limit) >= 0) {
                log.warn("预算超限: rule={}, scope={}/{}, usage={}, limit={}",
                        rule.getRuleName(), rule.getScopeType(), rule.getScopeId(), usage, limit);
                throw new BusinessException(ErrorCode.BUDGET_EXCEEDED,
                        String.format("预算规则「%s」已超限：当前消费 ¥%.4f / 上限 ¥%.4f",
                                rule.getRuleName(), usage, limit));
            }

            int threshold = rule.getAlertThreshold() != null ? rule.getAlertThreshold() : 80;
            BigDecimal thresholdAmount = limit.multiply(BigDecimal.valueOf(threshold))
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            if (usage.compareTo(thresholdAmount) >= 0) {
                log.warn("预算告警: rule={}, scope={}/{}, usage={}, limit={}, threshold={}%",
                        rule.getRuleName(), rule.getScopeType(), rule.getScopeId(),
                        usage, limit, threshold);
            }
        }
    }

    @Override
    public BudgetRuleDO createRule(BudgetRuleDTO dto) {
        BudgetRuleDO rule = new BudgetRuleDO();
        BeanUtils.copyProperties(dto, rule);
        rule.setStatus(1);
        rule.setTenantId(BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L);
        rule.setCreateBy(BaseContext.getUserId());
        rule.setCreateTime(LocalDateTime.now());
        rule.setUpdateTime(LocalDateTime.now());
        rule.setIsDeleted(0);
        budgetRuleMapper.insert(rule);
        log.info("预算规则创建: id={}, name={}", rule.getId(), rule.getRuleName());
        return rule;
    }

    @Override
    public void deleteRule(Long id) {
        BudgetRuleDO rule = budgetRuleMapper.selectById(id);
        if (rule == null || rule.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.BUDGET_RULE_NOT_FOUND);
        }
        Long currentTenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        if (!currentTenantId.equals(rule.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        rule.setIsDeleted(1);
        rule.setUpdateTime(LocalDateTime.now());
        budgetRuleMapper.updateById(rule);
        log.info("预算规则删除: id={}", id);
    }

    @Override
    public List<BudgetRuleDO> listRules() {
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        LambdaQueryWrapper<BudgetRuleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BudgetRuleDO::getTenantId, tenantId);
        wrapper.eq(BudgetRuleDO::getIsDeleted, 0);
        wrapper.orderByDesc(BudgetRuleDO::getCreateTime);
        return budgetRuleMapper.selectList(wrapper);
    }

    @Override
    public List<Map<String, Object>> getUsage() {
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        Long userId = BaseContext.getUserId();

        List<BudgetRuleDO> rules = listRules();
        List<Map<String, Object>> result = new ArrayList<>();

        for (BudgetRuleDO rule : rules) {
            BigDecimal usage = calculateUsage(rule, tenantId, null, userId);
            BigDecimal limit = rule.getLimitAmount();
            BigDecimal percent = limit.compareTo(BigDecimal.ZERO) > 0
                    ? usage.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Map<String, Object> item = new HashMap<>();
            item.put("ruleId", rule.getId());
            item.put("ruleName", rule.getRuleName());
            item.put("scopeType", rule.getScopeType());
            item.put("scopeId", rule.getScopeId());
            item.put("periodType", rule.getPeriodType());
            item.put("limitAmount", limit);
            item.put("currentUsage", usage);
            item.put("usagePercent", percent);
            item.put("alertThreshold", rule.getAlertThreshold());
            item.put("status", rule.getStatus());
            result.add(item);
        }

        return result;
    }

    private BigDecimal calculateUsage(BudgetRuleDO rule, Long tenantId, Long agentId, Long userId) {
        LocalDateTime periodStart = getPeriodStart(rule.getPeriodType());

        LambdaQueryWrapper<AgentTaskDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTaskDO::getTenantId, tenantId);
        wrapper.eq(AgentTaskDO::getStatus, TASK_COMPLETED);
        wrapper.eq(AgentTaskDO::getIsDeleted, 0);
        wrapper.ge(AgentTaskDO::getCreateTime, periodStart);

        if (SCOPE_AGENT.equals(rule.getScopeType()) && rule.getScopeId() != null) {
            wrapper.eq(AgentTaskDO::getAgentId, rule.getScopeId());
        }
        if (SCOPE_USER.equals(rule.getScopeType()) && rule.getScopeId() != null) {
            wrapper.eq(AgentTaskDO::getCreateBy, rule.getScopeId());
        }

        List<AgentTaskDO> tasks = agentTaskMapper.selectList(wrapper);

        BigDecimal totalCost = BigDecimal.ZERO;
        for (AgentTaskDO task : tasks) {
            int promptTokens = task.getPromptTokens() != null ? task.getPromptTokens() : 0;
            int completionTokens = task.getCompletionTokens() != null ? task.getCompletionTokens() : 0;
            BigDecimal cost = costService.calculateCost("default", "default", promptTokens, completionTokens);
            totalCost = totalCost.add(cost);
        }

        return totalCost.setScale(4, RoundingMode.HALF_UP);
    }

    private LocalDateTime getPeriodStart(String periodType) {
        if (PERIOD_DAILY.equals(periodType)) {
            return LocalDate.now().atStartOfDay();
        }
        if (PERIOD_MONTHLY.equals(periodType)) {
            return LocalDate.now().withDayOfMonth(1).atStartOfDay();
        }
        return LocalDate.now().atStartOfDay();
    }
}
