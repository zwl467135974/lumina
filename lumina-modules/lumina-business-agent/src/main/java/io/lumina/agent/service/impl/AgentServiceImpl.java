package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.engine.AgentExecutionEngine;
import io.lumina.agent.infrastructure.entity.AgentDO;
import io.lumina.agent.infrastructure.mapper.AgentMapper;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.agent.service.AgentService;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 服务实现
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentExecutionEngine agentExecutionEngine;

    @Autowired
    private AgentMapper agentMapper;

    /**
     * Domain -> DO 转换
     */
    private AgentDO toDO(Agent agent) {
        AgentDO agentDO = new AgentDO();
        BeanUtils.copyProperties(agent, agentDO);
        return agentDO;
    }

    /**
     * DO -> Domain 转换
     */
    private Agent toDomain(AgentDO agentDO) {
        Agent agent = new Agent();
        BeanUtils.copyProperties(agentDO, agent);
        return agent;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Agent createAgent(Agent agent) {
        log.info("创建 Agent: {}", agent.getAgentName());

        // 验证
        agent.validateName();
        agent.validateType();

        // 转换为 DO 并持久化
        AgentDO agentDO = toDO(agent);
        agentMapper.insert(agentDO);

        // 更新 ID
        agent.setAgentId(agentDO.getAgentId());

        log.info("Agent 创建成功: id={}", agent.getAgentId());
        return agent;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Agent updateAgent(Long agentId, Agent agent) {
        log.info("更新 Agent: id={}", agentId);

        Agent existingAgent = getAgentById(agentId);

        // 更新字段
        if (agent.getAgentName() != null) {
            existingAgent.setAgentName(agent.getAgentName());
        }
        if (agent.getAgentType() != null) {
            existingAgent.setAgentType(agent.getAgentType());
        }
        if (agent.getDescription() != null) {
            existingAgent.setDescription(agent.getDescription());
        }
        if (agent.getStatus() != null) {
            existingAgent.setStatus(agent.getStatus());
        }

        // 验证
        existingAgent.validateName();
        existingAgent.validateType();

        // 持久化到数据库
        AgentDO agentDO = toDO(existingAgent);
        agentMapper.updateById(agentDO);

        log.info("Agent 更新成功: id={}", agentId);
        return existingAgent;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAgent(Long agentId) {
        log.info("删除 Agent: id={}", agentId);

        Agent agent = getAgentById(agentId);

        // 逻辑删除
        agentMapper.deleteById(agentId);

        log.info("Agent 删除成功: id={}", agentId);
    }

    @Override
    public Agent getAgentById(Long agentId) {
        log.info("查询 Agent: id={}", agentId);

        AgentDO agentDO = agentMapper.selectById(agentId);

        if (agentDO == null) {
            throw new BusinessException("Agent 不存在: id=" + agentId);
        }

        return toDomain(agentDO);
    }

    @Override
    public PageResult<Agent> pageAgents(String agentName, String agentType, Integer pageNum, Integer pageSize) {
        log.info("分页查询 Agent: name={}, type={}, pageNum={}, pageSize={}",
                agentName, agentType, pageNum, pageSize);

        // 构建查询条件
        LambdaQueryWrapper<AgentDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(agentName)) {
            queryWrapper.like(AgentDO::getAgentName, agentName);
        }
        if (StringUtils.hasText(agentType)) {
            queryWrapper.eq(AgentDO::getAgentType, agentType);
        }

        // 分页查询
        Page<AgentDO> page = new Page<>(pageNum, pageSize);
        Page<AgentDO> doPage = agentMapper.selectPage(page, queryWrapper);

        // 转换为 Domain
        List<Agent> agentList = doPage.getRecords().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());

        // 构建分页结果
        PageResult<Agent> pageResult = new PageResult<>();
        pageResult.setList(agentList);
        pageResult.setTotal(doPage.getTotal());
        pageResult.setPageNum(pageNum);
        pageResult.setPageSize(pageSize);
        pageResult.setPages(doPage.getPages());

        return pageResult;
    }

    @Override
    public String executeAgent(Long agentId, String task) {
        log.info("执行 Agent: id={}, task={}", agentId, task);

        // 查询 Agent
        Agent agent = getAgentById(agentId);

        // 检查状态
        if (!agent.isActive()) {
            throw new BusinessException("Agent 未启用，无法执行任务");
        }

        // 构建配置
        AgentConfig config = new AgentConfig();
        config.setAgentName(agent.getAgentName());
        config.setAgentType(agent.getAgentType());

        // 执行 Agent
        ExecuteResult result = agentExecutionEngine.executeSync(
                agent.getAgentType().toLowerCase(),
                task,
                config
        );

        if (!result.getSuccess()) {
            throw new BusinessException("Agent 执行失败: " + result.getError());
        }

        log.info("Agent 执行成功: id={}", agentId);
        return result.getResult();
    }
}
