package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 启动对账：将指定状态的任务跨租户标记为 INTERRUPTED
     *
     * <p>服务重启后执行线程已消失，RUNNING/QUEUED（本地线程池模式）任务必然卡死。
     * INTERRUPTED 语义（借鉴 DeepSeek Harness 崩溃恢复）：执行已中断、结果未知、
     * 仅幂等操作可安全重试——与 FAILED（明确失败）和 CANCELLED（用户主动）区分。
     * 后台线程无租户上下文，标注 {@code @InterceptorIgnore} 跳过租户插件。
     *
     * @param status 待对账的源状态（RUNNING / QUEUED）
     * @return 影响行数
     * @since 3.11.0
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE lumina_agent_task SET status = 'INTERRUPTED', " +
            "error_message = '服务重启，任务中断（执行结果未知：仅幂等操作可安全重试）', " +
            "update_time = NOW() " +
            "WHERE status = #{status} AND is_deleted = 0")
    int markInterruptedOnStartup(@Param("status") String status);
}
