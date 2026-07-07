package io.lumina.agent.service;

import io.lumina.agent.api.dto.EvaluationDatasetDTO;
import io.lumina.agent.api.dto.EvaluationRunDTO;
import io.lumina.agent.evaluation.model.EvaluationDataset;
import io.lumina.agent.evaluation.model.RunReport;
import io.lumina.agent.infrastructure.entity.EvaluationRunDO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Agent 评估服务
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface EvaluationService {

    EvaluationDataset createDataset(EvaluationDatasetDTO dto);

    EvaluationDataset getDataset(Long id);

    List<EvaluationDataset> listDatasets(String name);

    void deleteDataset(Long id);

    /**
     * 从上传的 YAML 文件导入数据集
     */
    EvaluationDataset importDataset(MultipartFile file, String name, String agentType, String description);

    RunReport runEvaluation(Long datasetId, EvaluationRunDTO dto);

    /**
     * 异步执行评估，立即返回运行 ID（状态 RUNNING），后台完成后更新记录
     */
    Long runEvaluationAsync(Long datasetId, EvaluationRunDTO dto);

    RunReport getRunReport(Long runId);

    List<EvaluationRunDO> listRuns(Long datasetId);

    /**
     * 查询同一数据集的历史评估趋势（按时间正序，用于折线图）
     */
    List<EvaluationRunDO> getRunTrend(Long datasetId);
}
