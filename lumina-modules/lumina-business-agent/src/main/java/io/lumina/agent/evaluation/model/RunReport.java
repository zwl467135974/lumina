package io.lumina.agent.evaluation.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 评估运行报告
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class RunReport {

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
    private Map<String, CategoryStats> categoryStats;
    private List<CaseResult> results;

    @Data
    public static class CategoryStats {
        private int totalCases;
        private int passedCases;
        private double passRate;
        private double avgScore;
    }
}
