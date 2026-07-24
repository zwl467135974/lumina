package io.lumina.agent.service;

import io.lumina.agent.api.vo.AgentTraceVO;
import io.lumina.common.core.PageResult;

/**
 * Agent 推理链追踪查询服务
 *
 * @author Lumina Team
 * @since 3.7.0
 */
public interface AgentTraceService {

    /**
     * 分页查询 Trace 列表
     */
    PageResult<AgentTraceVO> list(Long agentId, String status, int pageNum, int pageSize);

    /**
     * 查询 Trace 详情（含完整 steps JSON）
     */
    AgentTraceVO getByUuid(String traceUuid);
}
