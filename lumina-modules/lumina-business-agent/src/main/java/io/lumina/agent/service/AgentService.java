package io.lumina.agent.service;

import io.lumina.agent.domain.model.Agent;
import io.lumina.common.core.PageResult;

/**
 * Agent 服务接口
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public interface AgentService {

    /**
     * 创建 Agent
     *
     * @param agent Agent 领域实体
     * @return 创建后的 Agent
     */
    Agent createAgent(Agent agent);

    /**
     * 更新 Agent
     *
     * @param agentId Agent ID
     * @param agent  Agent 领域实体
     * @return 更新后的 Agent
     */
    Agent updateAgent(Long agentId, Agent agent);

    /**
     * 删除 Agent
     *
     * @param agentId Agent ID
     */
    void deleteAgent(Long agentId);

    /**
     * 根据 ID 获取 Agent
     *
     * @param agentId Agent ID
     * @return Agent 领域实体
     */
    Agent getAgentById(Long agentId);

    /**
     * 分页查询 Agent 列表
     *
     * @param agentName Agent 名称（模糊查询）
     * @param agentType Agent 类型
     * @param pageNum    页码
     * @param pageSize   每页数量
     * @return 分页结果
     */
    PageResult<Agent> pageAgents(String agentName, String agentType, Integer pageNum, Integer pageSize);

    /**
     * 执行 Agent 任务
     *
     * @param agentId Agent ID
     * @param task    任务描述
     * @return 执行结果
     */
    String executeAgent(Long agentId, String task);
}
