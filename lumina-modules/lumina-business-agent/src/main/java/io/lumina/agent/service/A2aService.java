package io.lumina.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.lumina.agent.api.dto.a2a.A2aAgentCard;
import io.lumina.agent.api.dto.a2a.A2aTask;

import java.util.List;

/**
 * A2A（Agent2Agent）协议服务
 *
 * <p>把 Lumina Agent 暴露为 A2A 开放协议的远端 Agent：Agent Card 描述能力，
 * JSON-RPC message/send 派发任务（复用异步任务管线），tasks/get 轮询结果。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
public interface A2aService {

    /** 单个 Agent 的 Agent Card（url 指向 {baseUrl}/v1/a2a/agents/{agentId}） */
    A2aAgentCard getAgentCard(Long agentId, String baseUrl);

    /** 当前租户全部启用 Agent 的卡片（发现入口） */
    List<A2aAgentCard> listAgentCards(String baseUrl);

    /** JSON-RPC message/send：提交异步任务，返回 submitted 状态的 A2A Task */
    A2aTask messageSend(Long agentId, JsonNode params);

    /** JSON-RPC tasks/get：按任务 ID 查询状态/产出 */
    A2aTask taskGet(JsonNode params);

    /** JSON-RPC tasks/cancel：取消任务（仅 QUEUED/RUNNING 可取消） */
    A2aTask taskCancel(JsonNode params);
}
