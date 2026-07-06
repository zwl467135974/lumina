package io.lumina.agent.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 成本计算服务
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface CostService {

    /**
     * 计算单次执行费用
     *
     * @param provider         模型提供商
     * @param modelName        模型名称
     * @param promptTokens     输入 token 数
     * @param completionTokens 输出 token 数
     * @return 费用（元）
     */
    BigDecimal calculateCost(String provider, String modelName, int promptTokens, int completionTokens);

    /**
     * 查询租户消费汇总
     *
     * @return 汇总数据（totalTokens, totalCost, taskCount 等）
     */
    Map<String, Object> getTenantCostSummary();

    /**
     * 查询租户每日消费趋势
     *
     * @param days 查询天数（最近 N 天）
     * @return 每日消费列表（date, taskCount, promptTokens, completionTokens, totalTokens, cost）
     */
    List<Map<String, Object>> getCostTrend(int days);
}
