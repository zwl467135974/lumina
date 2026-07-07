package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.engine.AgentExecutionEngine;
import io.lumina.agent.infrastructure.entity.AgentDO;
import io.lumina.agent.infrastructure.entity.ConversationDO;
import io.lumina.agent.infrastructure.entity.PromptDO;
import io.lumina.agent.infrastructure.mapper.AgentMapper;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.framework.storage.FileService;
import io.lumina.framework.storage.entity.FileDO;
import io.lumina.agent.model.MultimodalImage;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.ConversationService;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import io.lumina.agent.model.StreamChunk;
import io.lumina.agent.model.StreamEventType;
import io.lumina.agent.service.PromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

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

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private FileService fileService;

    @Autowired
    private PromptService promptService;

    @Autowired
    private io.lumina.agent.security.PromptInjectionFilter promptInjectionFilter;

    @Autowired
    private io.lumina.agent.security.OutputSanitizer outputSanitizer;

    @Autowired
    private io.lumina.agent.security.AgentRateLimiter agentRateLimiter;

    @Autowired
    private io.lumina.agent.service.BudgetService budgetService;

    @Autowired(required = false)
    private io.lumina.agent.security.ContentModerationService contentModerationService;

    @org.springframework.beans.factory.annotation.Value("${lumina.agent.content-moderation.strict:false}")
    private boolean contentModerationStrict;

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

        // 转换�?DO 并持久化
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
        if (agent.getLlmConfig() != null) {
            existingAgent.setLlmConfig(agent.getLlmConfig());
        }
        if (agent.getTools() != null) {
            existingAgent.setTools(agent.getTools());
        }
        if (agent.getStatus() != null) {
            existingAgent.setStatus(agent.getStatus());
        }

        // 验证
        existingAgent.validateName();
        existingAgent.validateType();

        // 持久化到数据�?
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
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND, "Agent 不存�? id=" + agentId);
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

        // 转换�?Domain
        List<Agent> agentList = doPage.getRecords().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());

        // 构建分页结果
        PageResult<Agent> pageResult = new PageResult<>();
        pageResult.setList(agentList);
        pageResult.setTotal(doPage.getTotal());
        pageResult.setPageNum(pageNum);
        pageResult.setPageSize(pageSize);
        pageResult.setPages((int) doPage.getPages());

        return pageResult;
    }

    @Override
    public String executeAgent(Long agentId, String task, String conversationUuid) {
        log.info("执行 Agent: id={}, task={}, conversation={}", agentId, task, conversationUuid);

        agentRateLimiter.checkRateLimit(agentId);
        budgetService.checkBudget(agentId);

        // 查询 Agent
        Agent agent = getAgentById(agentId);

        // 检查状�?
        if (!agent.isActive()) {
            throw new BusinessException(ErrorCode.AGENT_NOT_ACTIVE);
        }

        // 安全检测：Prompt 注入
        promptInjectionFilter.check(task);

        moderateContent(task);

        // 构建配置
        AgentConfig config = buildExecutionConfig(agent);

        // 会话上下文校�?+ 保存用户消息到数据库
        String sessionId = resolveConversation(conversationUuid, agentId);
        if (sessionId != null) {
            conversationService.saveMessage(sessionId, "user", task, 0, null);
        }

        // 执行 Agent（引擎加载历史记�?+ 保存�?Redis�?
        ExecuteResult result = agentExecutionEngine.executeSync(
                agent.getAgentType().toLowerCase(),
                task,
                config,
                sessionId
        );

        if (!result.getSuccess()) {
            throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, "Agent 执行失败: " + result.getError());
        }

        // 输出脱敏
        String sanitizedResult = outputSanitizer.sanitize(result.getResult());

        // 保存助手回复到数据库
        if (sessionId != null) {
            Integer tokenCount = result.getTokenUsage() != null ? result.getTokenUsage().getTotalTokens() : 0;
            conversationService.saveMessage(sessionId, "assistant", sanitizedResult, tokenCount, result.getDuration());
            conversationService.incrementMessageCount(sessionId, 2);
        }

        log.info("Agent 执行成功: id={}", agentId);
        return sanitizedResult;
    }

    @Override
    public String executeAgentMultimodal(Long agentId, String task, List<String> fileUuids, String conversationUuid) {
        log.info("多模态执�?Agent: id={}, task={}, fileCount={}, conversation={}",
                agentId, task, fileUuids != null ? fileUuids.size() : 0, conversationUuid);

        agentRateLimiter.checkRateLimit(agentId);
        budgetService.checkBudget(agentId);

        Agent agent = getAgentById(agentId);
        if (!agent.isActive()) {
            throw new BusinessException(ErrorCode.AGENT_NOT_ACTIVE);
        }

        promptInjectionFilter.check(task);

        moderateContent(task);

        AgentConfig config = buildExecutionConfig(agent);

        String sessionId = resolveConversation(conversationUuid, agentId);

        // 从文件存储加载图片，转换�?MultimodalImage
        List<MultimodalImage> images = new ArrayList<>();
        if (fileUuids != null) {
            for (String uuid : fileUuids) {
                FileDO fileDO = fileService.getByUuid(uuid);
                if (fileDO == null || fileDO.getStatus() != 1) {
                    log.warn("文件不存在或已删�? {}", uuid);
                    continue;
                }
                byte[] bytes;
                try (java.io.InputStream is = fileService.download(uuid)) {
                    bytes = is.readAllBytes();
                } catch (Exception e) {
                    throw new BusinessException("读取图片失败: " + uuid, e);
                }
                images.add(new MultimodalImage(fileDO.getContentType(),
                        java.util.Base64.getEncoder().encodeToString(bytes)));
            }
        }

        // 序列�?file_ids JSON
        String fileIdsJson = null;
        if (fileUuids != null && !fileUuids.isEmpty()) {
            try {
                fileIdsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fileUuids);
            } catch (Exception e) {
                fileIdsJson = null;
            }
        }

        if (sessionId != null) {
            conversationService.saveMessage(sessionId, "user", task, 0, null, fileIdsJson);
        }

        ExecuteResult result = agentExecutionEngine.executeMultimodalSync(
                agent.getAgentType().toLowerCase(),
                task,
                images,
                config,
                sessionId
        );

        if (!result.getSuccess()) {
            throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, "Agent 执行失败: " + result.getError());
        }

        String sanitizedMultimodalResult = outputSanitizer.sanitize(result.getResult());

        if (sessionId != null) {
            Integer tokenCount = result.getTokenUsage() != null ? result.getTokenUsage().getTotalTokens() : 0;
            conversationService.saveMessage(sessionId, "assistant", sanitizedMultimodalResult, tokenCount, result.getDuration());
            conversationService.incrementMessageCount(sessionId, 2);
        }

        log.info("多模�?Agent 执行成功: id={}", agentId);
        return sanitizedMultimodalResult;
    }

    @Override
    public Flux<StreamChunk> executeAgentStream(Long agentId, String task, String conversationUuid) {
        log.info("流式执行 Agent: id={}, task={}, conversation={}", agentId, task, conversationUuid);

        if (task == null || task.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.AGENT_TASK_EMPTY);
        }

        agentRateLimiter.checkRateLimit(agentId);
        budgetService.checkBudget(agentId);

        promptInjectionFilter.check(task);

        moderateContent(task);

        // 查询 Agent
        Agent agent = getAgentById(agentId);

        // 检查状�?
        if (!agent.isActive()) {
            throw new BusinessException(ErrorCode.AGENT_NOT_ACTIVE);
        }

        // 构建配置
        AgentConfig config = buildExecutionConfig(agent);

        // 会话上下文校�?+ 保存用户消息到数据库
        String sessionId = resolveConversation(conversationUuid, agentId);
        if (sessionId != null) {
            conversationService.saveMessage(sessionId, "user", task, 0, null);
        }

        final String sid = sessionId;
        StringBuilder fullResponse = new StringBuilder();

        return agentExecutionEngine.executeStream(
                agent.getAgentType().toLowerCase(),
                task,
                config,
                sid
        )
        .doOnNext(chunk -> {
            String type = chunk.type();
            if (StreamEventType.FINAL.equals(type) || StreamEventType.AGENT_RESULT.equals(type)) {
                fullResponse.append(chunk.content());
            }
        })
        .doOnComplete(() -> {
            if (sid != null && fullResponse.length() > 0) {
                String sanitized = outputSanitizer.sanitize(fullResponse.toString());
                conversationService.saveMessage(sid, "assistant", sanitized, 0, null);
                conversationService.incrementMessageCount(sid, 2);
            }
        });
    }

    @Override
    public Flux<StreamChunk> executeAgentMultimodalStream(Long agentId, String task, List<String> fileUuids, String conversationUuid) {
        agentRateLimiter.checkRateLimit(agentId);
        budgetService.checkBudget(agentId);

        promptInjectionFilter.check(task);

        moderateContent(task);

        AgentDO agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
        }
        if (agent.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AGENT_NOT_ACTIVE);
        }

        AgentConfig config = buildExecutionConfig(toDomain(agent));

        String sessionId = resolveConversation(conversationUuid, agentId);

        List<MultimodalImage> images = new ArrayList<>();
        if (fileUuids != null) {
            for (String uuid : fileUuids) {
                FileDO fileDO = fileService.getByUuid(uuid);
                if (fileDO == null || fileDO.getStatus() != 1) {
                    log.warn("文件不存在或已删�? {}", uuid);
                    continue;
                }
                byte[] bytes;
                try (java.io.InputStream is = fileService.download(uuid)) {
                    bytes = is.readAllBytes();
                } catch (Exception e) {
                    throw new BusinessException("读取图片失败: " + uuid, e);
                }
                images.add(new MultimodalImage(fileDO.getContentType(),
                        java.util.Base64.getEncoder().encodeToString(bytes)));
            }
        }

        String fileIdsJson = null;
        if (fileUuids != null && !fileUuids.isEmpty()) {
            try {
                fileIdsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fileUuids);
            } catch (Exception e) {
                fileIdsJson = null;
            }
        }

        if (sessionId != null) {
            conversationService.saveMessage(sessionId, "user", task, 0, null, fileIdsJson);
        }

        final String sid = sessionId;
        StringBuilder fullResponse = new StringBuilder();

        return agentExecutionEngine.executeMultimodalStream(
                agent.getAgentType().toLowerCase(),
                task,
                images,
                config,
                sid
        )
        .doOnNext(chunk -> {
            String type = chunk.type();
            if (StreamEventType.FINAL.equals(type) || StreamEventType.AGENT_RESULT.equals(type)) {
                fullResponse.append(chunk.content());
            }
        })
        .doOnComplete(() -> {
            if (sid != null && fullResponse.length() > 0) {
                String sanitized = outputSanitizer.sanitize(fullResponse.toString());
                conversationService.saveMessage(sid, "assistant", sanitized, 0, null);
                conversationService.incrementMessageCount(sid, 2);
            }
        });
    }

    /**
     * 解析会话：校�?UUID 归属 Agent，返回会话标识（用作记忆 Key�?
     *
     * @param conversationUuid 会话 UUID（null 则返�?null�?
     * @param agentId          当前 Agent ID
     * @return 会话 UUID，或 null（无会话上下文）
     */
    private void moderateContent(String task) {
        if (contentModerationService != null) {
            io.lumina.agent.security.ModerationResult result = contentModerationService.moderate(task, contentModerationStrict);
            if (!result.isAllowed()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, result.getReason());
            }
        }
    }

    private String resolveConversation(String conversationUuid, Long agentId) {
        if (conversationUuid == null || conversationUuid.isEmpty()) {
            return null;
        }
        ConversationDO conv = conversationService.getByUuid(conversationUuid);
        if (!conv.getAgentId().equals(agentId)) {
            throw new BusinessException(ErrorCode.CONVERSATION_AGENT_MISMATCH);
        }
        return conv.getConversationUuid();
    }

    private AgentConfig buildExecutionConfig(Agent agent) {
        AgentConfig config = new AgentConfig();
        config.setAgentName(agent.getAgentName());
        config.setAgentType(agent.getAgentType());

        String agentType = agent.getAgentType();
        if (StringUtils.hasText(agentType)) {
            PromptDO activePrompt = promptService.getActive(agentType.toLowerCase());
            if (activePrompt != null && StringUtils.hasText(activePrompt.getContent())) {
                config.setPromptTemplate(activePrompt.getContent());
                log.info("使用 DB 激活 Prompt: name={}, version={}", activePrompt.getName(), activePrompt.getVersion());
            }
        }

        // 解析 Agent 的 LLM 配置（DB 持久化的 JSON）
        if (StringUtils.hasText(agent.getLlmConfig())) {
            try {
                AgentConfig.LLMConfig llmConfig = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(agent.getLlmConfig(), AgentConfig.LLMConfig.class);
                config.setLlmConfig(llmConfig);
                log.debug("使用 Agent 专属 LLM 配置: modelType={}, modelName={}",
                        llmConfig.getModelType(), llmConfig.getModelName());
            } catch (Exception e) {
                log.warn("解析 Agent LLM 配置失败，使用全局默认: {}", e.getMessage());
            }
        }

        // 解析 Agent 的工具列表
        if (StringUtils.hasText(agent.getTools())) {
            AgentConfig.ToolConfig toolConfig = new AgentConfig.ToolConfig();
            toolConfig.setTools(java.util.Arrays.asList(agent.getTools().split(",")));
            config.setToolConfig(toolConfig);
        }

        return config;
    }
}
