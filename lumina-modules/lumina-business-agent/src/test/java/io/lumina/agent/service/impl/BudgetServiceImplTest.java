package io.lumina.agent.service.impl;

import io.lumina.agent.api.dto.BudgetRuleDTO;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.infrastructure.entity.BudgetRuleDO;
import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import io.lumina.agent.infrastructure.mapper.BudgetRuleMapper;
import io.lumina.agent.service.CostService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * BudgetServiceImpl 单元测试
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceImplTest {

    @InjectMocks
    private BudgetServiceImpl budgetService;

    @Mock
    private BudgetRuleMapper budgetRuleMapper;

    @Mock
    private AgentTaskMapper agentTaskMapper;

    @Mock
    private CostService costService;

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void checkBudgetPassesWhenNoRules() {
        BaseContext.setTenantId(1L);
        when(budgetRuleMapper.selectList(any())).thenReturn(List.of());

        assertThatCode(() -> budgetService.checkBudget(10L))
                .doesNotThrowAnyException();
    }

    @Test
    void checkBudgetPassesWhenUnderLimit() {
        BaseContext.setTenantId(1L);

        BudgetRuleDO rule = new BudgetRuleDO();
        rule.setRuleName("tenant-daily");
        rule.setScopeType("TENANT");
        rule.setPeriodType("DAILY");
        rule.setLimitAmount(new BigDecimal("10.0000"));
        rule.setAlertThreshold(80);
        when(budgetRuleMapper.selectList(any())).thenReturn(List.of(rule));

        AgentTaskDO task = new AgentTaskDO();
        task.setPromptTokens(100);
        task.setCompletionTokens(50);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(costService.calculateCost(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new BigDecimal("0.5000"));

        assertThatCode(() -> budgetService.checkBudget(10L))
                .doesNotThrowAnyException();
    }

    @Test
    void checkBudgetThrowsWhenExceeded() {
        BaseContext.setTenantId(1L);

        BudgetRuleDO rule = new BudgetRuleDO();
        rule.setRuleName("tenant-daily");
        rule.setScopeType("TENANT");
        rule.setPeriodType("DAILY");
        rule.setLimitAmount(new BigDecimal("1.0000"));
        rule.setAlertThreshold(80);
        when(budgetRuleMapper.selectList(any())).thenReturn(List.of(rule));

        AgentTaskDO task = new AgentTaskDO();
        task.setPromptTokens(1000);
        task.setCompletionTokens(500);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(costService.calculateCost(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new BigDecimal("5.0000"));

        assertThatThrownBy(() -> budgetService.checkBudget(10L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createRulePersistsCorrectly() {
        BaseContext.setTenantId(1L);
        BaseContext.setUserId(5L);

        BudgetRuleDTO dto = new BudgetRuleDTO();
        dto.setRuleName("test-rule");
        dto.setScopeType("AGENT");
        dto.setScopeId(42L);
        dto.setPeriodType("MONTHLY");
        dto.setLimitAmount(new BigDecimal("100.0000"));
        dto.setAlertThreshold(90);

        BudgetRuleDO result = budgetService.createRule(dto);

        assertThat(result.getRuleName()).isEqualTo("test-rule");
        assertThat(result.getScopeType()).isEqualTo("AGENT");
        assertThat(result.getScopeId()).isEqualTo(42L);
        assertThat(result.getTenantId()).isEqualTo(1L);
        assertThat(result.getCreateBy()).isEqualTo(5L);
    }

    @Test
    void getUsageReturnsCalculatedPercentages() {
        BaseContext.setTenantId(1L);

        BudgetRuleDO rule = new BudgetRuleDO();
        rule.setId(1L);
        rule.setRuleName("tenant-daily");
        rule.setScopeType("TENANT");
        rule.setPeriodType("DAILY");
        rule.setLimitAmount(new BigDecimal("10.0000"));
        rule.setAlertThreshold(80);
        when(budgetRuleMapper.selectList(any())).thenReturn(List.of(rule));

        AgentTaskDO task = new AgentTaskDO();
        task.setPromptTokens(500);
        task.setCompletionTokens(200);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(costService.calculateCost(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new BigDecimal("2.2000"));

        var usage = budgetService.getUsage();

        assertThat(usage).hasSize(1);
        assertThat(usage.get(0).get("currentUsage")).isEqualTo(new BigDecimal("2.2000"));
        assertThat(usage.get(0).get("usagePercent")).isEqualTo(new BigDecimal("22.00"));
    }
}
