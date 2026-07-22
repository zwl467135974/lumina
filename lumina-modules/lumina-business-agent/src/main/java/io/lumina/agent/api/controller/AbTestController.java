package io.lumina.agent.api.controller;

import io.lumina.common.annotation.RequirePermission;
import io.lumina.agent.api.dto.ab.CreateAbExperimentDTO;
import io.lumina.agent.api.vo.AbExperimentVO;
import io.lumina.agent.service.AbTestService;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * A/B 测试管理 Controller
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@RestController
@RequirePermission("ab-test:view")
@RequestMapping("/api/v1/ab-tests")
@RequiredArgsConstructor
@Validated
@Tag(name = "A/B 测试", description = "实验管理、变体配置、效果报告")
public class AbTestController {

    private final AbTestService abTestService;

    @PostMapping
    @Audit(module = "ab_test", action = "CREATE")
    @Operation(summary = "创建实验")
    public R<AbExperimentVO> create(@RequestBody @jakarta.validation.Valid CreateAbExperimentDTO dto) {
        return R.success(abTestService.createExperiment(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询实验详情（含报告）")
    public R<AbExperimentVO> get(@PathVariable Long id) {
        return R.success(abTestService.getExperiment(id));
    }

    @GetMapping
    @Operation(summary = "查询实验列表")
    public R<List<AbExperimentVO>> list(
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String status) {
        return R.success(abTestService.listExperiments(agentId, status));
    }

    @PutMapping("/{id}/start")
    @Audit(module = "ab_test", action = "UPDATE")
    @Operation(summary = "启动实验")
    public R<Void> start(@PathVariable Long id) {
        abTestService.startExperiment(id);
        return R.success();
    }

    @PutMapping("/{id}/pause")
    @Audit(module = "ab_test", action = "UPDATE")
    @Operation(summary = "暂停实验")
    public R<Void> pause(@PathVariable Long id) {
        abTestService.pauseExperiment(id);
        return R.success();
    }

    @PutMapping("/{id}/complete")
    @Audit(module = "ab_test", action = "UPDATE")
    @Operation(summary = "完成实验")
    public R<Void> complete(@PathVariable Long id) {
        abTestService.completeExperiment(id);
        return R.success();
    }

    @DeleteMapping("/{id}")
    @Audit(module = "ab_test", action = "DELETE")
    @Operation(summary = "删除实验")
    public R<Void> delete(@PathVariable Long id) {
        abTestService.deleteExperiment(id);
        return R.success();
    }
}
