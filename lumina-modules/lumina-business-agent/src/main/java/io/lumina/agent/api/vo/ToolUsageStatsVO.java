package io.lumina.agent.api.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单工具使用统计 VO（时间窗内聚合）
 *
 * <p>successRate 等派生指标在 Java 侧计算（SQL 只做纯聚合）。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
public class ToolUsageStatsVO {

    private String toolName;

    /** 调用次数 */
    private Long calls;

    /** 成功次数 */
    private Long successCalls;

    /** 成功率（0-100，Java 侧计算） */
    private Double successRate;

    private Double avgDurationMs;

    private Long maxDurationMs;

    /** 平均结果字符数（上下文成本代理指标） */
    private Double avgResultChars;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastUsedAt;
}
