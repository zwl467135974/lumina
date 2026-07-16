package io.lumina.agent.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A/B 测试实验 VO
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Data
public class AbExperimentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private Long agentId;
    private Integer trafficPercent;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private List<AbVariantVO> variants;
    private AbExperimentReport report;

    @Data
    public static class AbVariantVO {
        private Long id;
        private String name;
        private Integer weight;
        private String llmConfig;
        private String promptName;
        private String description;
    }

    @Data
    public static class AbExperimentReport {
        private Long totalExposures;
        private List<VariantReport> variants;

        @Data
        public static class VariantReport {
            private String variantName;
            private Long exposures;
            private Double successRate;
            private Double avgLatencyMs;
            private Double avgTokens;
        }
    }
}
