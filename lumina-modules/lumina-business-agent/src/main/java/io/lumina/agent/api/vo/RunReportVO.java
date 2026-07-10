package io.lumina.agent.api.vo;

import io.lumina.agent.evaluation.model.CaseResult;
import io.lumina.agent.evaluation.model.RunReport;
import io.lumina.agent.evaluation.model.ScoringMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 评估运行报告 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long runId;
    private Long datasetId;
    private String datasetName;
    private Long agentId;
    private String agentType;
    private ScoringMethod scoringMethod;
    private double threshold;
    private int totalCases;
    private int passedCases;
    private double passRate;
    private double avgScore;
    private long avgLatencyMs;
    private int totalTokens;
    private Map<String, RunReport.CategoryStats> categoryStats;
    private List<CaseResult> results;

    public static RunReportVO from(RunReport report) {
        if (report == null) {
            return null;
        }
        return RunReportVO.builder()
                .runId(report.getRunId())
                .datasetId(report.getDatasetId())
                .datasetName(report.getDatasetName())
                .agentId(report.getAgentId())
                .agentType(report.getAgentType())
                .scoringMethod(report.getScoringMethod())
                .threshold(report.getThreshold())
                .totalCases(report.getTotalCases())
                .passedCases(report.getPassedCases())
                .passRate(report.getPassRate())
                .avgScore(report.getAvgScore())
                .avgLatencyMs(report.getAvgLatencyMs())
                .totalTokens(report.getTotalTokens())
                .categoryStats(report.getCategoryStats())
                .results(report.getResults())
                .build();
    }
}
