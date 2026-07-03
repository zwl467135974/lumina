package io.lumina.agent.service.impl;

import io.lumina.agent.infrastructure.entity.AgentDO;
import io.lumina.agent.infrastructure.mapper.AgentMapper;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.orchestration.engine.AgentExecutionHandler;
import io.lumina.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 执行处理器桥接实现
 *
 * <p>将编排引擎的 Agent 执行请求委托给 {@link AgentService}，
 * 实现 agent-core 编排引擎与 business-agent 模块的解耦。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExecutionHandlerBridge implements AgentExecutionHandler {

    private final AgentService agentService;
    private final AgentMapper agentMapper;

    @Override
    public String executeAgent(Long agentId, String task, String conversationUuid) {
        log.info("编排引擎委托 Agent 执行: agentId={}, taskLen={}", agentId, task != null ? task.length() : 0);

        AgentDO agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new RuntimeException("Agent 不存在: " + agentId);
        }
        if (agent.getStatus() != 1) {
            throw new RuntimeException("Agent 未启用: " + agentId);
        }

        String result = agentService.executeAgent(agentId, task, conversationUuid);
        log.info("Agent 执行完成: agentId={}, resultLen={}", agentId, result != null ? result.length() : 0);
        return result;
    }
}
