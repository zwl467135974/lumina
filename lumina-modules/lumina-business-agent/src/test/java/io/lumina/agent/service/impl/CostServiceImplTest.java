package io.lumina.agent.service.impl;

import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.infrastructure.entity.ModelPricingDO;
import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import io.lumina.agent.infrastructure.mapper.ModelPricingMapper;
import io.lumina.common.core.BaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CostServiceImpl 单元测试
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
class CostServiceImplTest {

    @InjectMocks
    private CostServiceImpl costService;

    @Mock
    private ModelPricingMapper modelPricingMapper;

    @Mock
    private AgentTaskMapper agentTaskMapper;

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void calculateCostWithExactPricing() {
        ModelPricingDO pricing = new ModelPricingDO();
        pricing.setInputPrice(new BigDecimal("0.001000"));
        pricing.setOutputPrice(new BigDecimal("0.002000"));
        when(modelPricingMapper.selectOne(any())).thenReturn(pricing);

        BigDecimal cost = costService.calculateCost("deepseek", "deepseek-chat", 1000, 500);

        // 1000/1000 * 0.001 + 500/1000 * 0.002 = 0.001 + 0.001 = 0.002
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.002000"));
    }

    @Test
    void calculateCostFallsBackToDefault() {
        when(modelPricingMapper.selectOne(any())).thenReturn(null);

        BigDecimal cost = costService.calculateCost("unknown", "unknown-model", 2000, 1000);

        // Falls back to hardcoded: 2000/1000 * 0.002 + 1000/1000 * 0.006 = 0.004 + 0.006 = 0.010
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.010000"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getTenantCostSummaryAggregatesCorrectly() {
        BaseContext.setTenantId(1L);

        AgentTaskDO task1 = new AgentTaskDO();
        task1.setAgentId(10L);
        task1.setPromptTokens(500);
        task1.setCompletionTokens(200);
        task1.setTotalTokens(700);

        AgentTaskDO task2 = new AgentTaskDO();
        task2.setAgentId(20L);
        task2.setPromptTokens(300);
        task2.setCompletionTokens(100);
        task2.setTotalTokens(400);

        when(agentTaskMapper.selectList(any())).thenReturn(List.of(task1, task2));

        ModelPricingDO defaultPricing = new ModelPricingDO();
        defaultPricing.setInputPrice(new BigDecimal("0.002000"));
        defaultPricing.setOutputPrice(new BigDecimal("0.006000"));
        when(modelPricingMapper.selectOne(any())).thenReturn(defaultPricing);

        Map<String, Object> summary = costService.getTenantCostSummary();

        assertThat(summary.get("taskCount")).isEqualTo(2);
        assertThat(summary.get("totalPromptTokens")).isEqualTo(800L);
        assertThat(summary.get("totalCompletionTokens")).isEqualTo(300L);
        assertThat(summary.get("totalTokens")).isEqualTo(1100L);
        assertThat(summary.get("currency")).isEqualTo("CNY");

        List<Map<String, Object>> topAgents = (List<Map<String, Object>>) summary.get("topAgents");
        assertThat(topAgents).hasSize(2);
        // Agent 10 should cost more (more tokens)
        assertThat(topAgents.get(0).get("agentId")).isEqualTo(10L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getTenantCostSummaryWithNoTasksReturnsZeros() {
        BaseContext.setTenantId(2L);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> summary = costService.getTenantCostSummary();

        assertThat(summary.get("taskCount")).isEqualTo(0);
        assertThat(summary.get("totalTokens")).isEqualTo(0L);
        assertThat(summary.get("totalCost")).isEqualTo(new BigDecimal("0.0000"));
        List<Map<String, Object>> topAgents = (List<Map<String, Object>>) summary.get("topAgents");
        assertThat(topAgents).isEmpty();
    }
}
