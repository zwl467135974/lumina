package io.lumina.agent.service;

import io.lumina.agent.api.dto.AgentTaskRequestDTO;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;

/**
 * Agent 异步任务服务
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface AgentTaskService {

    /** 提交异步任务 */
    AgentTaskDO submitTask(Long agentId, AgentTaskRequestDTO dto);

    /** 查询任务详情 */
    AgentTaskDO getTask(String taskUuid);
}
