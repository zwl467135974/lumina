package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.ToolUsageDO;
import io.lumina.agent.api.vo.ToolUsageStatsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工具使用明细 Mapper（聚合统计走 @Select，条件 CRUD 由 BaseMapper 提供）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Mapper
public interface ToolUsageMapper extends BaseMapper<ToolUsageDO> {

    /**
     * 按工具聚合指定时间窗内的使用统计（成功率等派生指标在 Java 侧计算）
     */
    @Select("SELECT tool_name AS toolName, " +
            "COUNT(*) AS calls, " +
            "COALESCE(SUM(success), 0) AS successCalls, " +
            "COALESCE(AVG(duration_ms), 0) AS avgDurationMs, " +
            "COALESCE(MAX(duration_ms), 0) AS maxDurationMs, " +
            "COALESCE(AVG(result_chars), 0) AS avgResultChars, " +
            "MAX(create_time) AS lastUsedAt " +
            "FROM lumina_tool_usage " +
            "WHERE tenant_id = #{tenantId} " +
            "AND agent_id = #{agentId} " +
            "AND create_time >= #{since} " +
            "GROUP BY tool_name " +
            "ORDER BY calls DESC")
    List<ToolUsageStatsVO> aggregateByTool(@Param("tenantId") Long tenantId,
                                           @Param("agentId") Long agentId,
                                           @Param("since") LocalDateTime since);
}
