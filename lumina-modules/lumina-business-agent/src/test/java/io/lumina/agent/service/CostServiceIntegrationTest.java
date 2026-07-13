package io.lumina.agent.service;

import io.lumina.agent.BaseIntegrationTest;
import io.lumina.agent.infrastructure.entity.ModelPricingDO;
import io.lumina.agent.infrastructure.mapper.ModelPricingMapper;
import io.lumina.common.core.BaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CostService 集成测试
 *
 * <p>验证 pricing fallback 链(精确匹配 → default → 硬编码)与聚合查询。
 *
 * @author Lumina Team
 * @since 3.1.0
 */
@Transactional
class CostServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CostService costService;

    @Autowired
    private ModelPricingMapper modelPricingMapper;

    private static final Long TENANT_ID = 9301L;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(TENANT_ID);
        BaseContext.setUserId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void calculateCostExactMatch() {
        // 插入精确匹配的定价
        ModelPricingDO pricing = new ModelPricingDO();
        pricing.setProvider("test-provider");
        pricing.setModelName("test-model");
        pricing.setInputPrice(new BigDecimal("0.010000"));
        pricing.setOutputPrice(new BigDecimal("0.030000"));
        pricing.setCurrency("CNY");
        pricing.setIsActive(1);
        modelPricingMapper.insert(pricing);

        // 1000/1000 × 0.01 + 500/1000 × 0.03 = 0.01 + 0.015 = 0.025
        BigDecimal cost = costService.calculateCost("test-provider", "test-model", 1000, 500);

        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.025000"));
    }

    @Test
    void calculateCostFallbackToDefault() {
        // V11 种子数据有 default/default = 0.002/0.006
        // 不存在的 provider+model 应 fallback 到 default
        BigDecimal cost = costService.calculateCost("nonexistent-provider", "nonexistent-model", 1000, 1000);

        // 1000/1000 × 0.002 + 1000/1000 × 0.006 = 0.002 + 0.006 = 0.008
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.008000"));
    }

    @Test
    void calculateCostWithZeroTokens() {
        BigDecimal cost = costService.calculateCost("default", "default", 0, 0);
        assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getTenantCostSummaryReturnsMap() {
        // 该租户可能没有 COMPLETED 任务，summary 应返回零值结构
        Map<String, Object> summary = costService.getTenantCostSummary();

        assertThat(summary).isNotNull();
        assertThat(summary).containsKey("taskCount");
        assertThat(summary).containsKey("totalTokens");
        assertThat(summary).containsKey("totalCost");
    }

    @Test
    void getCostTrendReturnsList() {
        // 查询最近 7 天趋势
        List<Map<String, Object>> trend = costService.getCostTrend(7);

        assertThat(trend).isNotNull();
        // 无数据时可能为空列表，有数据时每条应含 date 字段
        if (!trend.isEmpty()) {
            assertThat(trend.get(0)).containsKey("date");
        }
    }
}
