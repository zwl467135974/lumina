package io.lumina.agent.api.controller;

import io.lumina.agent.service.CostService;
import io.lumina.common.core.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 成本管理 Controller
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cost")
@RequiredArgsConstructor
public class CostController {

    private final CostService costService;

    /**
     * 查询租户消费汇总
     */
    @GetMapping("/summary")
    public R<Map<String, Object>> getCostSummary() {
        log.info("查询租户消费汇总");
        return R.success(costService.getTenantCostSummary());
    }
}
