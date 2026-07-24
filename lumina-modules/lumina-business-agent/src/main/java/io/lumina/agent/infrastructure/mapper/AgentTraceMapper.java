package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.AgentTraceDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * Agent 推理链追踪 Mapper
 *
 * @author Lumina Team
 * @since 3.7.0
 */
@Mapper
public interface AgentTraceMapper extends BaseMapper<AgentTraceDO> {

    /**
     * 批量删除早于指定时间的 trace 记录（分批 LIMIT 避免大事务锁表）
     *
     * @param cutoff    截止时间（删除此时间之前的记录）
     * @param batchSize 每批删除上限
     * @return 实际删除的行数
     */
    @Delete("DELETE FROM lumina_agent_trace WHERE create_time < #{cutoff} LIMIT #{batchSize}")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
