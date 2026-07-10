package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Agent 异步任务 Mapper
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTaskDO> {

    @Select("SELECT DATE(create_time) AS date, " +
            "provider, " +
            "model_name AS modelName, " +
            "COUNT(*) AS taskCount, " +
            "COALESCE(SUM(prompt_tokens), 0) AS promptTokens, " +
            "COALESCE(SUM(completion_tokens), 0) AS completionTokens, " +
            "COALESCE(SUM(total_tokens), 0) AS totalTokens " +
            "FROM lumina_agent_task " +
            "WHERE tenant_id = #{tenantId} " +
            "AND is_deleted = 0 " +
            "AND status = 'COMPLETED' " +
            "AND create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(create_time), provider, model_name " +
            "ORDER BY date ASC")
    List<Map<String, Object>> selectDailyTrend(@Param("tenantId") Long tenantId, @Param("days") int days);
}
