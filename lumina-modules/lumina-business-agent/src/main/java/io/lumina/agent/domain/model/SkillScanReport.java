package io.lumina.agent.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 技能上架体检报告（领域模型）
 *
 * <p>体检结论由发现项驱动：含 HIGH 则拒绝（REJECTED），含 MEDIUM 则标记可疑
 * （FLAGGED，默认禁用待人工复核），否则通过（PASSED）。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillScanReport {

    /** 体检结论：REJECTED / FLAGGED / PASSED */
    private String verdict;

    /** 发现项列表 */
    private List<Finding> findings = new ArrayList<>();

    /** 体检时间（ISO-8601 字符串，避免序列化依赖 JSR310 模块） */
    private String scannedAt;

    /**
     * 单条发现（severity + 规则名 + 命中摘要）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Finding {

        /** 严重级别：HIGH / MEDIUM / LOW */
        private String severity;

        /** 规则标识（如 prompt-injection、dangerous-command） */
        private String rule;

        /** 命中内容摘要（截断展示，不回显全文） */
        private String detail;
    }

    public static SkillScanReport of(List<Finding> findings) {
        String verdict = verdictOf(findings);
        return new SkillScanReport(verdict, findings,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    /**
     * 业务规则：HIGH → REJECTED；MEDIUM → FLAGGED；否则 PASSED
     */
    public static String verdictOf(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return "PASSED";
        }
        boolean hasHigh = findings.stream().anyMatch(f -> "HIGH".equals(f.getSeverity()));
        if (hasHigh) {
            return "REJECTED";
        }
        boolean hasMedium = findings.stream().anyMatch(f -> "MEDIUM".equals(f.getSeverity()));
        return hasMedium ? "FLAGGED" : "PASSED";
    }

    /** 是否可直接启用（仅 PASSED 允许导入即启用） */
    public boolean isClean() {
        return "PASSED".equals(verdict);
    }

    /** 是否拒绝入库（REJECTED 不落库） */
    public boolean isRejected() {
        return "REJECTED".equals(verdict);
    }
}
