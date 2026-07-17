package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.AgentTriggerDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 定时触发器 Mapper
 *
 * <p>poller 状态更新在 @Scheduled 后台线程执行（BaseContext 无租户），
 * 相关 SQL 均按主键定位并标注 {@code @InterceptorIgnore(tenantLine = "true")} 跳过租户插件。
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@Mapper
public interface AgentTriggerMapper extends BaseMapper<AgentTriggerDO> {

    /**
     * poller 查询：跨租户扫描启用的且到时间的触发器
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM lumina_agent_trigger WHERE enabled = 1 AND next_fire_at <= NOW() AND is_deleted = 0")
    List<AgentTriggerDO> selectDueTriggers();

    /**
     * 触发成功：重置失败计数并前进 next_fire_at
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE lumina_agent_trigger SET fail_count = 0, last_status = #{status}, last_error = NULL, " +
            "last_fire_at = NOW(), next_fire_at = #{nextFireAt} WHERE id = #{id}")
    int updateFired(@Param("id") Long id, @Param("status") String status,
                    @Param("nextFireAt") LocalDateTime nextFireAt);

    /**
     * 触发失败：写入失败计数/原因，失败过多时由调用方置 enabled=0
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE lumina_agent_trigger SET fail_count = #{failCount}, last_status = 'FAILED', " +
            "last_error = #{error}, last_fire_at = NOW(), next_fire_at = #{nextFireAt}, " +
            "enabled = #{enabled} WHERE id = #{id}")
    int updateFireFailed(@Param("id") Long id, @Param("failCount") int failCount,
                         @Param("error") String error, @Param("nextFireAt") LocalDateTime nextFireAt,
                         @Param("enabled") int enabled);

    /**
     * 仅前进 next_fire_at（misfire SKIP 策略）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE lumina_agent_trigger SET next_fire_at = #{nextFireAt} WHERE id = #{id}")
    int updateNextFireAt(@Param("id") Long id, @Param("nextFireAt") LocalDateTime nextFireAt);
}
