package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.EvaluationDatasetDTO;
import io.lumina.agent.api.dto.EvaluationRunDTO;
import io.lumina.agent.evaluation.model.EvaluationDataset;
import io.lumina.agent.evaluation.model.RunReport;
import io.lumina.agent.infrastructure.entity.EvaluationRunDO;
import io.lumina.agent.service.EvaluationService;
import io.lumina.common.core.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Agent 评估 API
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    @PostMapping("/datasets")
    public R<EvaluationDataset> createDataset(@Valid @RequestBody EvaluationDatasetDTO dto) {
        log.info("创建评估数据集: name={}", dto.getName());
        return R.success(evaluationService.createDataset(dto));
    }

    /**
     * 从 YAML 文件导入数据集
     */
    @PostMapping("/datasets/import")
    public R<EvaluationDataset> importDataset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "agentType", required = false) String agentType,
            @RequestParam(value = "description", required = false) String description) {
        log.info("导入评估数据集: fileName={}, name={}", file.getOriginalFilename(), name);
        return R.success(evaluationService.importDataset(file, name, agentType, description));
    }

    @GetMapping("/datasets")
    public R<List<EvaluationDataset>> listDatasets(@RequestParam(required = false) String name) {
        return R.success(evaluationService.listDatasets(name));
    }

    @GetMapping("/datasets/{id}")
    public R<EvaluationDataset> getDataset(@PathVariable Long id) {
        return R.success(evaluationService.getDataset(id));
    }

    @DeleteMapping("/datasets/{id}")
    public R<Void> deleteDataset(@PathVariable Long id) {
        evaluationService.deleteDataset(id);
        return R.success();
    }

    @PostMapping("/datasets/{id}/runs")
    public R<RunReport> runEvaluation(@PathVariable Long id, @Valid @RequestBody EvaluationRunDTO dto) {
        log.info("执行 Agent 评估: datasetId={}, agentId={}", id, dto.getAgentId());
        return R.success(evaluationService.runEvaluation(id, dto));
    }

    /**
     * 异步执行评估（适用于大数据集），返回运行 ID 供前端轮询
     */
    @PostMapping("/datasets/{id}/runs/async")
    public R<Long> runEvaluationAsync(@PathVariable Long id, @Valid @RequestBody EvaluationRunDTO dto) {
        log.info("异步执行 Agent 评估: datasetId={}, agentId={}", id, dto.getAgentId());
        return R.success(evaluationService.runEvaluationAsync(id, dto));
    }

    @GetMapping("/runs")
    public R<List<EvaluationRunDO>> listRuns(@RequestParam(required = false) Long datasetId) {
        return R.success(evaluationService.listRuns(datasetId));
    }

    @GetMapping("/runs/{id}")
    public R<RunReport> getRunReport(@PathVariable Long id) {
        return R.success(evaluationService.getRunReport(id));
    }

    /**
     * 查询同一数据集的历史评估趋势（按时间正序，用于折线图）
     */
    @GetMapping("/datasets/{id}/trend")
    public R<List<EvaluationRunDO>> getRunTrend(@PathVariable Long id) {
        return R.success(evaluationService.getRunTrend(id));
    }
}
