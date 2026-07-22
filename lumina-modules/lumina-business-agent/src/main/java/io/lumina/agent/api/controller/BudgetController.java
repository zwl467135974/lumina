package io.lumina.agent.api.controller;

import io.lumina.common.annotation.RequirePermission;
import io.lumina.agent.api.dto.BudgetRuleDTO;
import io.lumina.agent.api.vo.BudgetRuleVO;
import io.lumina.agent.service.BudgetService;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;

/**
 * 预算管理 Controller
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequirePermission("budget:list")
@RequestMapping("/api/v1/budget")
@RequiredArgsConstructor
@Validated
public class BudgetController {

    private final BudgetService budgetService;

    /**
     * 查询预算规则列表
     */
    @GetMapping("/rules")
    public R<List<BudgetRuleVO>> listRules() {
        List<BudgetRuleVO> list = budgetService.listRules().stream()
                .map(BudgetRuleVO::from)
                .toList();
        return R.success(list);
    }

    /**
     * 创建预算规则
     */
    @Audit(module = "budget", action = "CREATE", description = "创建预算规则")
    @PostMapping("/rules")
    public R<BudgetRuleVO> createRule(@Valid @RequestBody BudgetRuleDTO dto) {
        log.info("创建预算规则: {}", dto.getRuleName());
        return R.success(BudgetRuleVO.from(budgetService.createRule(dto)));
    }

    /**
     * 删除预算规则
     */
    @Audit(module = "budget", action = "DELETE", description = "删除预算规则")
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
