package io.lumina.agent.service;

import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.model.MultimodalImage;
import io.lumina.agent.model.StreamChunk;
import io.lumina.common.core.PageResult;
import reactor.core.publisher.Flux;

import java.util.List;

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
     * 执行 Agent 任务（带会话上下文）
     *
     * @param agentId          Agent ID
     * @param task             任务描述
     * @param conversationUuid 会话 UUID（null 表示无会话上下文，即单轮无状态）
     * @return 执行结果
     */
    String executeAgent(Long agentId, String task, String conversationUuid);

    /**
     * 执行 Agent 任务并返回完整执行结果（含 Token 用量与耗时）
     *
     * <p>与 {@link #executeAgent(Long, String, String)} 走同一执行管线
     * （限流/预算/Prompt 注入检测/审计/输出脱敏），供 OpenAI 兼容层等需要 usage 的调用方使用。
     *
     * @param agentId          Agent ID
     * @param task             任务描述
     * @param conversationUuid 会话 UUID（null 表示无会话上下文）
     * @return 完整执行结果（result 字段为脱敏后文本）
     */
    io.lumina.agent.model.ExecuteResult executeAgentForResult(Long agentId, String task, String conversationUuid);

    /**
     * 执行多模态 Agent 任务（文本 + 图片，带会话上下文）
     *
     * @param agentId          Agent ID
     * @param task             任务描述
     * @param fileUuids        图片文件 UUID 列表
     * @param conversationUuid 会话 UUID（null 表示无会话上下文）
     * @return 执行结果
     */
    String executeAgentMultimodal(Long agentId, String task, List<String> fileUuids, String conversationUuid);

    /**
     * 执行多模态 Agent 任务并返回完整执行结果（含 Token 用量与耗时）
     *
     * <p>与 {@link #executeAgentMultimodal(Long, String, List, String)} 走同一执行管线，
     * 供需要 usage 数据的调用方（如异步任务统计）使用。
     *
     * @param agentId          Agent ID
     * @param task             任务描述
     * @param fileUuids        文件 UUID 列表
     * @param conversationUuid 会话 UUID（null 表示无会话上下文）
     * @return 完整执行结果（result 字段为脱敏后文本）
     */
    io.lumina.agent.model.ExecuteResult executeAgentMultimodalForResult(
            Long agentId, String task, List<String> fileUuids, String conversationUuid);

    /**
     * 流式执行 Agent 任务（带会话上下文）
     *
     * @param conversationUuid 会话 UUID（null 表示无会话上下文）
     */
    Flux<StreamChunk> executeAgentStream(Long agentId, String task, String conversationUuid);

    /**
     * 流式执行多模态 Agent 任务（文本 + 图片，SSE 流式返回）
     *
     * @param agentId         Agent ID
     * @param task            任务描述
     * @param fileUuids       图片文件 UUID 列表
     * @param conversationUuid 会话 UUID（null 表示无会话上下文）
     */
    Flux<StreamChunk> executeAgentMultimodalStream(Long agentId, String task, List<String> fileUuids, String conversationUuid);

    /** 兼容重载：执行 Agent（无会话上下文） */
    default String executeAgent(Long agentId, String task) {
        return executeAgent(agentId, task, null);
    }

    /** 兼容重载：流式执行 Agent（无会话上下文） */
    default Flux<StreamChunk> executeAgentStream(Long agentId, String task) {
        return executeAgentStream(agentId, task, null);
    }
}
