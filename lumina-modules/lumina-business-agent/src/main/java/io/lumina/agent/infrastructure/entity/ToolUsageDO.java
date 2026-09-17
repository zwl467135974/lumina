package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具使用明细 DO（append-only，仅聚合读取，不做更新/删除）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
@TableName("lumina_tool_usage")
public class ToolUsageDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agentId;

    private String agentName;

    private String toolName;

    /** 1=成功 0=失败 */
    private Integer success;

    private Long durationMs;

    /** 入参字符数（上下文成本代理指标） */
    private Integer inputChars;

    /** 结果字符数（截断前） */
    private Integer resultChars;

    private Long tenantId;

    private LocalDateTime createTime;
}
