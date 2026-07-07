package io.lumina.agent.service;

import io.lumina.agent.api.dto.EvaluationDatasetDTO;
import io.lumina.agent.api.dto.EvaluationRunDTO;
import io.lumina.agent.evaluation.model.EvaluationDataset;
import io.lumina.agent.evaluation.model.RunReport;
import io.lumina.agent.infrastructure.entity.EvaluationRunDO;

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

    RunReport runEvaluation(Long datasetId, EvaluationRunDTO dto);

    RunReport getRunReport(Long runId);

    List<EvaluationRunDO> listRuns(Long datasetId);
}
