package io.lumina.agent.service;

import io.lumina.agent.api.dto.BudgetRuleDTO;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.infrastructure.entity.BudgetRuleDO;
import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import io.lumina.agent.infrastructure.mapper.BudgetRuleMapper;
import io.lumina.agent.service.impl.BudgetServiceImpl;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import io.lumina.framework.cache.RedisCacheManager;
import io.lumina.notification.event.NotificationEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BudgetService 单元测试
 *
 * <p>覆盖预算规则创建、用量查询、预算检查（含阈值告警去重）。
 *
 * @author Lumina Team
 * @since 3.6.0
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceUnitTest {

    @Mock
    private BudgetRuleMapper budgetRuleMapper;
    @Mock
    private AgentTaskMapper agentTaskMapper;
    @Mock
    private CostService costService;
    @Mock
    private NotificationEventPublisher notificationEventPublisher;
    @Mock
    private RedisCacheManager redisCacheManager;

    @InjectMocks
    private BudgetServiceImpl budgetService;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(0L);
        BaseContext.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void createRulePersistsCorrectly() {
        BudgetRuleDTO dto = new BudgetRuleDTO();
        dto.setRuleName("日预算");
        dto.setScopeType("AGENT");
        dto.setScopeId(100L);
        dto.setPeriodType("DAILY");
        dto.setLimitAmount(new BigDecimal("10.00"));
        dto.setAlertThreshold(80);

        BudgetRuleDO result = budgetService.createRule(dto);

        assertThat(result.getRuleName()).isEqualTo("日预算");
        assertThat(result.getStatus()).isEqualTo(1);
        verify(budgetRuleMapper).insert(any(BudgetRuleDO.class));
    }

    @Test
    void checkBudgetPassesWhenNoRules() {
        when(budgetRuleMapper.selectList(any())).thenReturn(List.of());
        // 无规则不应抛异常
        budgetService.checkBudget(100L);
    }

    @Test
    void checkBudgetBlocksWhenExceeded() {
        BudgetRuleDO rule = new BudgetRuleDO();
        rule.setId(1L);
        rule.setRuleName("低限额");
        rule.setScopeType("AGENT");
        rule.setScopeId(100L);
        rule.setPeriodType("DAILY");
        rule.setLimitAmount(new BigDecimal("0.01"));
        rule.setAlertThreshold(80);
        rule.setStatus(1);

        AgentTaskDO expensiveTask = new AgentTaskDO();
        expensiveTask.setPromptTokens(50000);
        expensiveTask.setCompletionTokens(50000);
        expensiveTask.setModelName("deepseek-chat");
        expensiveTask.setProvider("deepseek");

        when(budgetRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(expensiveTask));
        when(costService.calculateCost(any(), any(), anyInt(), anyInt()))
                .thenReturn(new BigDecimal("100.00"));

        assertThatThrownBy(() -> budgetService.checkBudget(100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void checkBudgetAlertsOnceDueToRedisDedup() {
        BudgetRuleDO rule = new BudgetRuleDO();
        rule.setId(1L);
        rule.setRuleName("告警测试");
        rule.setScopeType("AGENT");
        rule.setScopeId(100L);
        rule.setPeriodType("DAILY");
        rule.setLimitAmount(new BigDecimal("100.00"));
        rule.setAlertThreshold(80);
        rule.setStatus(1);

        AgentTaskDO task = new AgentTaskDO();
        task.setPromptTokens(100);
        task.setCompletionTokens(100);
        task.setModelName("deepseek-chat");
        task.setProvider("deepseek");

        when(budgetRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(costService.calculateCost(any(), any(), anyInt(), anyInt()))
                .thenReturn(new BigDecimal("90.00")); // 90% > 80% threshold

        // 第一次调用：Redis 无标记 → 发告警
        when(redisCacheManager.exists(anyString())).thenReturn(false);
        budgetService.checkBudget(100L);
        verify(notificationEventPublisher, times(1)).publish(any());

        // 第二次调用：Redis 有标记 → 跳过告警
        when(redisCacheManager.exists(anyString())).thenReturn(true);
        budgetService.checkBudget(100L);
        // 仍然只通知了 1 次（第二次被去重）
        verify(notificationEventPublisher, times(1)).publish(any());
    }

    @Test
    void deleteRuleRemovesRecord() {
        BudgetRuleDO rule = new BudgetRuleDO();
        rule.setId(1L);
        rule.setTenantId(0L);
        rule.setIsDeleted(0);
        when(budgetRuleMapper.selectById(1L)).thenReturn(rule);

        budgetService.deleteRule(1L);
        verify(budgetRuleMapper).deleteById(1L);
    }
}
