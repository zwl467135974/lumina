package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.infrastructure.entity.ModelPricingDO;
import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import io.lumina.agent.infrastructure.mapper.ModelPricingMapper;
import io.lumina.agent.service.CostService;
import io.lumina.common.core.BaseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本计算服务实现
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostServiceImpl implements CostService {

    private static final BigDecimal TOKENS_PER_THOUSAND = new BigDecimal("1000");

    private final ModelPricingMapper modelPricingMapper;
    private final AgentTaskMapper agentTaskMapper;

    @Override
    public BigDecimal calculateCost(String provider, String modelName, int promptTokens, int completionTokens) {
        ModelPricingDO pricing = findPricing(provider, modelName);

        BigDecimal inputCost = pricing.getInputPrice()
                .multiply(BigDecimal.valueOf(promptTokens))
                .divide(TOKENS_PER_THOUSAND, 6, RoundingMode.HALF_UP);

        BigDecimal outputCost = pricing.getOutputPrice()
                .multiply(BigDecimal.valueOf(completionTokens))
                .divide(TOKENS_PER_THOUSAND, 6, RoundingMode.HALF_UP);

        return inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    }

    @Override
    public Map<String, Object> getTenantCostSummary() {
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;

        LambdaQueryWrapper<AgentTaskDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTaskDO::getTenantId, tenantId);
        wrapper.eq(AgentTaskDO::getIsDeleted, 0);
        wrapper.eq(AgentTaskDO::getStatus, "COMPLETED");

        List<AgentTaskDO> tasks = agentTaskMapper.selectList(wrapper);

        long totalPromptTokens = 0;
        long totalCompletionTokens = 0;
        long totalTokens = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        Map<Long, AgentCostAccumulator> agentCosts = new HashMap<>();

        for (AgentTaskDO task : tasks) {
            totalPromptTokens += safeInt(task.getPromptTokens());
            totalCompletionTokens += safeInt(task.getCompletionTokens());
            totalTokens += safeInt(task.getTotalTokens());

            BigDecimal taskCost = calculateCost("default", "default",
                    safeInt(task.getPromptTokens()), safeInt(task.getCompletionTokens()));
            totalCost = totalCost.add(taskCost);

            agentCosts.computeIfAbsent(task.getAgentId(), k -> new AgentCostAccumulator())
                    .add(safeInt(task.getTotalTokens()), taskCost);
        }

        List<Map<String, Object>> topAgents = agentCosts.entrySet().stream()
                .sorted((a, b) -> b.getValue().cost.compareTo(a.getValue().cost))
                .limit(10)
                .map(entry -> {
                    Map<String, Object> agent = new HashMap<>();
                    agent.put("agentId", entry.getKey());
                    agent.put("tokens", entry.getValue().tokens);
                    agent.put("cost", entry.getValue().cost.setScale(4, RoundingMode.HALF_UP));
                    return agent;
                })
                .toList();

        Map<String, Object> summary = new HashMap<>();
        summary.put("taskCount", tasks.size());
        summary.put("totalPromptTokens", totalPromptTokens);
        summary.put("totalCompletionTokens", totalCompletionTokens);
        summary.put("totalTokens", totalTokens);
        summary.put("totalCost", totalCost.setScale(4, RoundingMode.HALF_UP));
        summary.put("currency", "CNY");
        summary.put("topAgents", topAgents);

        return summary;
    }

    @Override
    public List<Map<String, Object>> getCostTrend(int days) {
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        int effectiveDays = Math.max(1, Math.min(days, 365));

        List<Map<String, Object>> rawTrend = agentTaskMapper.selectDailyTrend(tenantId, effectiveDays);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rawTrend) {
            int promptTokens = toInt(row.get("promptTokens"));
            int completionTokens = toInt(row.get("completionTokens"));
            int totalTokens = toInt(row.get("totalTokens"));

            BigDecimal cost = calculateCost("default", "default", promptTokens, completionTokens);

            Map<String, Object> dataPoint = new HashMap<>();
            dataPoint.put("date", String.valueOf(row.get("date")));
            dataPoint.put("taskCount", toInt(row.get("taskCount")));
            dataPoint.put("promptTokens", promptTokens);
            dataPoint.put("completionTokens", completionTokens);
            dataPoint.put("totalTokens", totalTokens);
            dataPoint.put("cost", cost.setScale(4, RoundingMode.HALF_UP));
            result.add(dataPoint);
        }

        return result;
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private ModelPricingDO findPricing(String provider, String modelName) {
        LambdaQueryWrapper<ModelPricingDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelPricingDO::getProvider, provider != null ? provider : "default");
        wrapper.eq(ModelPricingDO::getModelName, modelName != null ? modelName : "default");
        wrapper.eq(ModelPricingDO::getIsActive, 1);
        wrapper.last("LIMIT 1");
        ModelPricingDO pricing = modelPricingMapper.selectOne(wrapper);
        if (pricing != null) {
            return pricing;
        }

        LambdaQueryWrapper<ModelPricingDO> fallback = new LambdaQueryWrapper<>();
        fallback.eq(ModelPricingDO::getProvider, "default");
        fallback.eq(ModelPricingDO::getModelName, "default");
        fallback.eq(ModelPricingDO::getIsActive, 1);
        fallback.last("LIMIT 1");
        ModelPricingDO defaultPricing = modelPricingMapper.selectOne(fallback);
        if (defaultPricing != null) {
            return defaultPricing;
        }

        log.warn("未找到模型价格配置，使用硬编码默认值");
        ModelPricingDO hardcoded = new ModelPricingDO();
        hardcoded.setInputPrice(new BigDecimal("0.002000"));
        hardcoded.setOutputPrice(new BigDecimal("0.006000"));
        return hardcoded;
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private static class AgentCostAccumulator {
        long tokens = 0;
        BigDecimal cost = BigDecimal.ZERO;

        void add(int t, BigDecimal c) {
            tokens += t;
            cost = cost.add(c);
        }
    }
}
