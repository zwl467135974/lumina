package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.BudgetRuleDTO;
import io.lumina.agent.infrastructure.entity.BudgetRuleDO;
import io.lumina.agent.service.BudgetService;
import io.lumina.common.core.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预算管理 Controller
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    /**
     * 查询预算规则列表
     */
    @GetMapping("/rules")
    public R<List<BudgetRuleDO>> listRules() {
        return R.success(budgetService.listRules());
    }

    /**
     * 创建预算规则
     */
    @PostMapping("/rules")
    public R<BudgetRuleDO> createRule(@Valid @RequestBody BudgetRuleDTO dto) {
        log.info("创建预算规则: {}", dto.getRuleName());
        return R.success(budgetService.createRule(dto));
    }

    /**
     * 删除预算规则
     */
    @DeleteMapping("/rules/{id}")
    public R<Void> deleteRule(@PathVariable Long id) {
        log.info("删除预算规则: id={}", id);
        budgetService.deleteRule(id);
        return R.success();
    }

    /**
     * 查询预算使用情况
     */
    @GetMapping("/usage")
    public R<List<Map<String, Object>>> getUsage() {
        return R.success(budgetService.getUsage());
    }
}
