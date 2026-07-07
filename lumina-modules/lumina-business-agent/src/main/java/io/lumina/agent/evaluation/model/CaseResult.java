package io.lumina.agent.evaluation.model;

import lombok.Data;

/**
 * 单条评估结果
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class CaseResult {
    private String caseId;
    private String input;
    private String expected;
    private String actual;
    private double score;
    private String scoreDetail;
    private boolean passed;
    private long latencyMs;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private String errorMessage;
    private String category;
}
