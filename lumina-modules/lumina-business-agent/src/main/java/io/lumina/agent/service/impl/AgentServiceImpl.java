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
import io.lumina.common.core.BaseContext;
import io.lumina.framework.storage.FileService;
import io.lumina.framework.storage.entity.FileDO;
import io.lumina.agent.model.MultimodalContent;
import io.lumina.agent.model.MultimodalDocument;
import io.lumina.agent.model.MultimodalImage;
import io.lumina.agent.rag.PdfOcrProcessor;
import io.lumina.agent.security.AgentConcurrencyLimiter;
import io.lumina.agent.security.AgentRateLimiter;
import io.lumina.agent.security.ContentModerationService;
import io.lumina.agent.security.OutputSanitizer;
import io.lumina.agent.security.PromptInjectionFilter;
import io.lumina.agent.service.AbTestService;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.BudgetService;
import io.lumina.agent.service.ConversationService;
import io.lumina.agent.service.KnowledgeBaseService;
import io.lumina.agent.service.ReflectiveMemoryService;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import io.lumina.agent.model.StreamChunk;
import io.lumina.agent.model.StreamEventType;
import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import io.lumina.agent.service.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Agent 服务实现
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentExecutionEngine agentExecutionEngine;

    private final AgentMapper agentMapper;

    private final ConversationService conversationService;

    private final FileService fileService;

    private final PromptService promptService;

    private final KnowledgeBaseService knowledgeBaseService;

    private final PromptInjectionFilter promptInjectionFilter;

    private final OutputSanitizer outputSanitizer;

    private final AgentRateLimiter agentRateLimiter;

    private final AgentConcurrencyLimiter concurrencyLimiter;

    private final AgentTaskMapper agentTaskMapper;

    private final BudgetService budgetService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // 可选依赖（Bean 不一定存在），保留字段注入 + required=false
    @Autowired(required = false)
    private ContentModerationService contentModerationService;

    @Autowired(required = false)
    private AbTestService abTestService;

    @Autowired(required = false)
    private PdfOcrProcessor pdfOcrProcessor;

    @Autowired(required = false)
    private ReflectiveMemoryService reflectiveMemoryService;

    @Autowired
    @Qualifier("agentTaskExecutor")
    private Executor agentTaskExecutor;

    @Value("${lumina.agent.content-moderation.strict:false}")
    private boolean contentModerationStrict;

    private record MultimodalContext(Agent agent, AgentConfig config, String sessionId,
                                     List<MultimodalContent> contents, String fileIdsJson) {}

    /**
     * Domain -> DO 转换
     */
    private AgentDO toDO(Agent agent) {
        AgentDO agentDO = new AgentDO();
        BeanUtils.copyProperties(agent, agentDO);
        if (agentDO.getTenantId() == null) {
            agentDO.setTenantId(BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L);
        }
        return agentDO;
    }

    /**
     * 获取当前租户 ID
     */
    private Long currentTenantId() {
        return BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
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
        if (agent.getLlmConfig() != null) {
            existingAgent.setLlmConfig(agent.getLlmConfig());
        }
        if (agent.getTools() != null) {
            existingAgent.setTools(agent.getTools());
        }
        if (agent.getStatus() != null) {
            existingAgent.setStatus(agent.getStatus());
        }
        if (agent.getRateLimit() != null) {
            existingAgent.setRateLimit(agent.getRateLimit());
        }
        if (agent.getMaxConcurrent() != null) {
            existingAgent.setMaxConcurrent(agent.getMaxConcurrent());
        }

        // 验证
        existingAgent.validateName();
        existingAgent.validateType();

        // 持久化到数据库
        AgentDO agentDO = toDO(existingAgent);
        agentMapper.updateById(agentDO);

        log.info("Agent 更新成功: id={}", agentId);
        agentExecutionEngine.evictCache(agentId);
        return existingAgent;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAgent(Long agentId) {
        log.info("删除 Agent: id={}", agentId);

        AgentDO agentDO = agentMapper.selectById(agentId);
        if (agentDO == null || !currentTenantId().equals(agentDO.getTenantId())) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND, "Agent 不存在 id=" + agentId);
        }

        agentMapper.deleteById(agentId);
        agentExecutionEngine.evictCache(agentId);

        log.info("Agent 删除成功: id={}", agentId);
    }

    @Override
    public Agent getAgentById(Long agentId) {
        log.info("查询 Agent: id={}", agentId);

        AgentDO agentDO = agentMapper.selectById(agentId);

        if (agentDO == null || !currentTenantId().equals(agentDO.getTenantId())) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND, "Agent 不存在 id=" + agentId);
        }

        return toDomain(agentDO);
    }

    @Override
    public PageResult<Agent> pageAgents(String agentName, String agentType, Integer pageNum, Integer pageSize) {
        log.info("分页查询 Agent: name={}, type={}, pageNum={}, pageSize={}",
                agentName, agentType, pageNum, pageSize);

        LambdaQueryWrapper<AgentDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AgentDO::getTenantId, currentTenantId());
        if (StringUtils.hasText(agentName)) {
            queryWrapper.like(AgentDO::getAgentName, agentName);
        }
        if (StringUtils.hasText(agentType)) {
            queryWrapper.eq(AgentDO::getAgentType, agentType);
        }

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
        pageResult.setPages((int) doPage.getPages());

        return pageResult;
    }

    @Override
    public String executeAgent(Long agentId, String task, String conversationUuid) {
        return executeAgentForResult(agentId, task, conversationUuid).getResult();
    }

    @Override
    public ExecuteResult executeAgentForResult(Long agentId, String task, String conversationUuid) {
        log.info("执行 Agent: id={}, task={}, conversation={}", agentId, task, conversationUuid);

        // 先查询 Agent（用于读取 per-agent 限流配置与状态检查）
        Agent agent = getAgentById(agentId);

        agentRateLimiter.checkRateLimit(agentId, agent.getRateLimit());
        budgetService.checkBudget(agentId);

        // 并发许可：maxConcurrent>0 时限制同时执行数
        boolean concurrencyAcquired = concurrencyLimiter.acquire(agentId, agent.getMaxConcurrent());
        try {
            // 检查状态
            if (!agent.isActive()) {
                throw new BusinessException(ErrorCode.AGENT_NOT_ACTIVE);
            }

            // 安全检测：Prompt 注入
            promptInjectionFilter.check(task);

            moderateContent(task);

            // 会话上下文校验
            String sessionId = resolveConversation(conversationUuid, agentId);

            // 构建配置（传入 sessionId 用于 A/B 变体粘滞分配）
            AgentConfig config = buildExecutionConfig(agent, sessionId);

            // 保存用户消息到数据库
            if (sessionId != null) {
                conversationService.saveMessage(sessionId, "user", task, 0, null);
            }

            // 执行 Agent（引擎加载历史记忆 + 保存到 Redis）
            ExecuteResult result = agentExecutionEngine.executeSync(
                    agent.getAgentType().toLowerCase(),
                    task,
                    config,
                    sessionId
            );

            // A/B 曝光记录
            recordAbExposure(result, sessionId);

            if (!result.getSuccess()) {
                throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, "Agent 执行失败: " + result.getError());
            }

            // 输出脱敏
            String sanitizedResult = outputSanitizer.sanitize(result.getResult());
            result.setResult(sanitizedResult);

            // 保存助手回复到数据库
            if (sessionId != null) {
                Integer tokenCount = result.getTokenUsage() != null ? result.getTokenUsage().getTotalTokens() : 0;
                conversationService.saveMessage(sessionId, "assistant", sanitizedResult, tokenCount, result.getDuration());
                conversationService.incrementMessageCount(sessionId, 2);
            }

            // Reflective Memory: 异步提取关键事实（不阻塞用户响应）
            triggerReflectiveMemory(agentId, sessionId, task, sanitizedResult);

            // 记录同步执行的 token 用量到 task 表（供成本/预算统计）
            recordSyncTask(agentId, result);

            log.info("Agent 执行成功: id={}", agentId);
            return result;
        } finally {
            if (concurrencyAcquired) {
                concurrencyLimiter.release(agentId);
            }
        }
    }

    @Override
    public String executeAgentMultimodal(Long agentId, String task, List<String> fileUuids, String conversationUuid) {
        return executeAgentMultimodalForResult(agentId, task, fileUuids, conversationUuid).getResult();
    }

    @Override
    public ExecuteResult executeAgentMultimodalForResult(Long agentId, String task, List<String> fileUuids, String conversationUuid) {
        MultimodalContext ctx = prepareMultimodalExecution(agentId, task, fileUuids, conversationUuid);
        boolean concurrencyAcquired = concurrencyLimiter.acquire(agentId, ctx.agent().getMaxConcurrent());
        try {
            ExecuteResult result = agentExecutionEngine.executeMultimodalSync(
                    ctx.agent().getAgentType().toLowerCase(),
                    task,
                    ctx.contents(),
                    ctx.config(),
                    ctx.sessionId()
            );

            if (!result.getSuccess()) {
                throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, "Agent 执行失败: " + result.getError());
            }

            String sanitizedResult = outputSanitizer.sanitize(result.getResult());
            result.setResult(sanitizedResult);

            if (ctx.sessionId() != null) {
                Integer tokenCount = result.getTokenUsage() != null ? result.getTokenUsage().getTotalTokens() : 0;
                conversationService.saveMessage(ctx.sessionId(), "assistant", sanitizedResult, tokenCount, result.getDuration());
                conversationService.incrementMessageCount(ctx.sessionId(), 2);
            }

            log.info("多模态 Agent 执行成功: id={}", agentId);
            return result;
        } finally {
            if (concurrencyAcquired) {
                concurrencyLimiter.release(agentId);
            }
        }
    }

    @Override
    public Flux<StreamChunk> executeAgentStream(Long agentId, String task, String conversationUuid) {
        log.info("流式执行 Agent: id={}, task={}, conversation={}", agentId, task, conversationUuid);

        if (task == null || task.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.AGENT_TASK_EMPTY);
        }

        // 先查询 Agent（用于读取 per-agent 限流配置与状态检查）
        Agent agent = getAgentById(agentId);

        agentRateLimiter.checkRateLimit(agentId, agent.getRateLimit());
        budgetService.checkBudget(agentId);

        promptInjectionFilter.check(task);

        moderateContent(task);

        // 检查状态
        if (!agent.isActive()) {
            throw new BusinessException(ErrorCode.AGENT_NOT_ACTIVE);
        }

        // 会话上下文校验
        String sessionId = resolveConversation(conversationUuid, agentId);

        // 构建配置（含 A/B 变体分配）
        AgentConfig config = buildExecutionConfig(agent, sessionId);

        // 保存用户消息
        if (sessionId != null) {
            conversationService.saveMessage(sessionId, "user", task, 0, null);
        }

        final String sid = sessionId;
        final StringBuffer fullResponse = new StringBuffer();
        final java.util.concurrent.atomic.AtomicReference<ExecuteResult.TokenUsage> streamTokenUsage =
                new java.util.concurrent.atomic.AtomicReference<>(null);
        boolean concurrencyAcquired = concurrencyLimiter.acquire(agentId, agent.getMaxConcurrent());

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
                if (chunk.tokenUsage() != null) {
                    streamTokenUsage.set(chunk.tokenUsage());
                }
            }
        })
        .doOnComplete(() -> {
            if (sid != null && fullResponse.length() > 0) {
                String sanitized = outputSanitizer.sanitize(fullResponse.toString());
                ExecuteResult.TokenUsage usage = streamTokenUsage.get();
                int tokenCount = usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
                conversationService.saveMessage(sid, "assistant", sanitized, tokenCount, null);
                conversationService.incrementMessageCount(sid, 2);
            }
        })
        .doFinally(signalType -> {
            if (concurrencyAcquired) {
                concurrencyLimiter.release(agentId);
            }
        });
    }

    @Override
    public Flux<StreamChunk> executeAgentMultimodalStream(Long agentId, String task, List<String> fileUuids, String conversationUuid) {
        MultimodalContext ctx = prepareMultimodalExecution(agentId, task, fileUuids, conversationUuid);

        final String sid = ctx.sessionId();
        final StringBuffer fullResponse = new StringBuffer();
        final java.util.concurrent.atomic.AtomicReference<ExecuteResult.TokenUsage> streamTokenUsage =
                new java.util.concurrent.atomic.AtomicReference<>(null);
        boolean concurrencyAcquired = concurrencyLimiter.acquire(agentId, ctx.agent().getMaxConcurrent());

        return agentExecutionEngine.executeMultimodalStream(
                ctx.agent().getAgentType().toLowerCase(),
                task,
                ctx.contents(),
                ctx.config(),
                sid
        )
        .doOnNext(chunk -> {
            String type = chunk.type();
            if (StreamEventType.FINAL.equals(type) || StreamEventType.AGENT_RESULT.equals(type)) {
                fullResponse.append(chunk.content());
                if (chunk.tokenUsage() != null) {
                    streamTokenUsage.set(chunk.tokenUsage());
                }
            }
        })
        .doOnComplete(() -> {
            if (sid != null && fullResponse.length() > 0) {
                String sanitized = outputSanitizer.sanitize(fullResponse.toString());
                ExecuteResult.TokenUsage usage = streamTokenUsage.get();
                int tokenCount = usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
                conversationService.saveMessage(sid, "assistant", sanitized, tokenCount, null);
                conversationService.incrementMessageCount(sid, 2);
            }
        })
        .doFinally(signalType -> {
            if (concurrencyAcquired) {
                concurrencyLimiter.release(agentId);
            }
        });
    }

    private MultimodalContext prepareMultimodalExecution(Long agentId, String task, List<String> fileUuids, String conversationUuid) {
        log.info("多模态执行 Agent: id={}, task={}, fileCount={}, conversation={}",
                agentId, task, fileUuids != null ? fileUuids.size() : 0, conversationUuid);

        // 先查询 Agent（用于读取 per-agent 限流配置与状态检查）
        Agent agent = getAgentById(agentId);

        agentRateLimiter.checkRateLimit(agentId, agent.getRateLimit());
        budgetService.checkBudget(agentId);

        promptInjectionFilter.check(task);
        moderateContent(task);

        if (!agent.isActive()) {
            throw new BusinessException(ErrorCode.AGENT_NOT_ACTIVE);
        }

        String sessionId = resolveConversation(conversationUuid, agentId);
        AgentConfig config = buildExecutionConfig(agent, sessionId);

        List<MultimodalContent> contents = loadFiles(fileUuids);
        String fileIdsJson = serializeFileIds(fileUuids);

        if (sessionId != null) {
            conversationService.saveMessage(sessionId, "user", task, 0, null, fileIdsJson);
        }

        return new MultimodalContext(agent, config, sessionId, contents, fileIdsJson);
    }

    /**
     * 加载文件并按类型构造多模态内容
     *
     * <p>图片 → {@link MultimodalImage}（Base64）；
     * PDF/Word/文本 → {@link MultimodalDocument}（解析提取文本）。
     *
     * @since 3.3.0
     */
    private List<MultimodalContent> loadFiles(List<String> fileUuids) {
        List<MultimodalContent> contents = new ArrayList<>();
        if (fileUuids == null) {
            return contents;
        }
        for (String uuid : fileUuids) {
            FileDO fileDO = fileService.getByUuid(uuid);
            if (fileDO == null || fileDO.getStatus() != 1) {
                log.warn("文件不存在或已删除: {}", uuid);
                continue;
            }
            String contentType = fileDO.getContentType() != null ? fileDO.getContentType() : "";
            String filename = fileDO.getOriginalName() != null ? fileDO.getOriginalName() : uuid;

            if (contentType.startsWith("image/")) {
                // 图片：Base64 直接投递
                byte[] bytes;
                try (java.io.InputStream is = fileService.download(uuid)) {
                    bytes = is.readAllBytes();
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.FILE_READ_FAILED, "读取图片失败: " + uuid, e);
                }
                contents.add(new MultimodalImage(contentType,
                        java.util.Base64.getEncoder().encodeToString(bytes)));
            } else {
                // 文档（PDF/Word/文本）：解析提取文本
                String text = extractDocumentText(uuid, filename, contentType);
                if (text != null && !text.isBlank()) {
                    contents.add(MultimodalDocument.of(text, filename));
                    log.info("文档文本提取成功: file={}, textLen={}", filename, text.length());
                } else {
                    // PDF 可能为扫描件 → 尝试 OCR
                    boolean likelyScanned = contentType.contains("pdf")
                            || (filename != null && filename.toLowerCase().endsWith(".pdf"));
                    if (likelyScanned && pdfOcrProcessor != null) {
                        // 下载文件到临时路径尝试 OCR
                        java.nio.file.Path ocrTempFile = downloadToTempFile(uuid, filename, contentType);
                        try {
                            String ocrText = pdfOcrProcessor.processPdf(ocrTempFile);
                            if (!ocrText.isBlank()) {
                                // 截断防止超长文本
                                contents.add(MultimodalDocument.of(ocrText, filename));
                                log.info("文档 OCR 识别成功: file={}, textLen={}", filename, ocrText.length());
                                continue;
                            }
                        } finally {
                            try { java.nio.file.Files.deleteIfExists(ocrTempFile); } catch (Exception ignored) {}
                        }
                    }

                    // OCR 后仍为空 → 给出提示
                    String hint = likelyScanned
                            ? "（注意：文档 " + filename + " 似乎是扫描件或图片型 PDF，无法提取文本。"
                            + "请提供可复制文字的电子版 PDF 或纯文本文件。）"
                            : "（注意：文档 " + filename + " 文本内容为空，已跳过。）";
                    contents.add(MultimodalDocument.of(hint, filename));
                    log.warn("文档文本提取为空: file={}, likelyScanned={}", filename, likelyScanned);
                }
            }
        }
        return contents;
    }

    /**
     * 提取文档文本（PDF/Word/纯文本）
     *
     * <p>复用 AgentScope Reader（与 RAG 入库同一路径），下载到临时文件后解析。
     *
     * @since 3.3.0
     */
    private String extractDocumentText(String uuid, String filename, String contentType) {
        java.nio.file.Path tempFile = null;
        try (java.io.InputStream is = fileService.download(uuid)) {
            String suffix = guessFileSuffix(filename, contentType);
            tempFile = java.nio.file.Files.createTempFile("lumina_mm_", suffix);
            java.nio.file.Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            io.agentscope.core.rag.reader.ReaderInput input =
                    io.agentscope.core.rag.reader.ReaderInput.fromPath(tempFile);

            java.util.List<io.agentscope.core.rag.model.Document> docs = null;
            String fmt = contentType.toLowerCase();
            // 用大 chunkSize 减少分块数，便于拼全文本喂 LLM
            int largeChunk = 50000;
            if (fmt.contains("pdf") || filename.toLowerCase().endsWith(".pdf")) {
                docs = new io.agentscope.core.rag.reader.PDFReader(
                        largeChunk, io.agentscope.core.rag.reader.SplitStrategy.PARAGRAPH, 0)
                        .read(input).block();
            } else if (fmt.contains("word") || filename.toLowerCase().endsWith(".doc")
                    || filename.toLowerCase().endsWith(".docx")) {
                docs = new io.agentscope.core.rag.reader.WordReader(
                        largeChunk, io.agentscope.core.rag.reader.SplitStrategy.PARAGRAPH, 0,
                        false, true, io.agentscope.core.rag.reader.TableFormat.MARKDOWN)
                        .read(input).block();
            } else {
                // 纯文本类（txt/md/csv/json 等）：直接读字符串
                return java.nio.file.Files.readString(tempFile);
            }

            if (docs == null || docs.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (var doc : docs) {
                String chunkText = doc.getMetadata() != null ? doc.getMetadata().getContentText() : null;
                if (chunkText != null) {
                    sb.append(chunkText).append("\n\n");
                }
            }
            return sb.toString().trim();

        } catch (Exception e) {
            log.error("文档文本提取失败: file={}, error={}", filename, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_READ_FAILED, "文档解析失败: " + filename, e);
        } finally {
            if (tempFile != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 下载文件到临时路径（用于 OCR 处理）
     */
    private java.nio.file.Path downloadToTempFile(String uuid, String filename, String contentType) {
        java.nio.file.Path tempFile = null;
        try (java.io.InputStream is = fileService.download(uuid)) {
            String suffix = guessFileSuffix(filename, contentType);
            tempFile = java.nio.file.Files.createTempFile("lumina_ocr_", suffix);
            java.nio.file.Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Path result = tempFile;
            tempFile = null; // 成功 → 交给调用方清理
            return result;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FILE_READ_FAILED, "下载文件失败: " + uuid, e);
        } finally {
            // 失败时清理已创建但未返回的临时文件
            if (tempFile != null) {
                try { java.nio.file.Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 根据文件名或 contentType 推断临时文件后缀
     */
    private String guessFileSuffix(String filename, String contentType) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0) {
                return filename.substring(dot);
            }
        }
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "application/msword" -> ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            default -> ".txt";
        };
    }

    private String serializeFileIds(List<String> fileUuids) {
        if (fileUuids == null || fileUuids.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(fileUuids);
        } catch (Exception e) {
            return null;
        }
    }

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

    /**
     * A/B 变体上下文 ThreadLocal（供执行后记录曝光使用）
     */
    private final ThreadLocal<io.lumina.agent.service.AbTestService.VariantContext> abVariantHolder = new ThreadLocal<>();

    private AgentConfig buildExecutionConfig(Agent agent) {
        return buildExecutionConfig(agent, null);
    }

    private AgentConfig buildExecutionConfig(Agent agent, String conversationId) {
        AgentConfig config = new AgentConfig();
        config.setAgentId(agent.getAgentId());
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
                AgentConfig.LLMConfig llmConfig = objectMapper
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

        // A/B 测试变体注入（如果有活跃实验，用变体配置覆盖 LLM/Prompt）
        if (abTestService != null) {
            try {
                io.lumina.agent.service.AbTestService.VariantContext variant =
                        abTestService.assignVariant(agent.getAgentId(), conversationId);
                if (variant != null) {
                    abVariantHolder.set(variant);
                    if (variant.llmConfig() != null) {
                        // 变体 LLM 配置覆盖 Agent 默认配置
                        AgentConfig.LLMConfig merged = variant.llmConfig();
                        // 合并：变体有的字段覆盖，没有的保留 Agent 原配置
                        AgentConfig.LLMConfig base = config.getLlmConfig();
                        if (base != null && merged.getModelType() == null) merged.setModelType(base.getModelType());
                        if (base != null && merged.getModelName() == null) merged.setModelName(base.getModelName());
                        if (base != null && merged.getApiKey() == null) merged.setApiKey(base.getApiKey());
                        config.setLlmConfig(merged);
                    }
                    if (variant.promptName() != null) {
                        // 变体指定了 Prompt，加载该 Prompt
                        PromptDO variantPrompt = promptService.getActive(variant.promptName().toLowerCase());
                        if (variantPrompt != null && StringUtils.hasText(variantPrompt.getContent())) {
                            config.setPromptTemplate(variantPrompt.getContent());
                        }
                    }
                    log.info("A/B 变体生效: experiment={}, variant={}", variant.experimentId(), variant.variantName());
                }
            } catch (Exception e) {
                log.debug("A/B 变体分配失败（不影响执行）: {}", e.getMessage());
            }
        }

        // 注入 Agent 挂载的知识库 ID（执行时按 kbId 过滤 RAG 检索）
        try {
            List<Long> kbIds = knowledgeBaseService.getAgentKnowledgeBaseIds(agent.getAgentId());
            if (kbIds != null && !kbIds.isEmpty()) {
                config.setKnowledgeBaseIds(kbIds);
            }
        } catch (Exception e) {
            log.debug("加载 Agent 知识库挂载失败（不影响执行）: {}", e.getMessage());
        }

        return config;
    }

    /**
     * 记录 A/B 测试曝光（执行后调用）
     */
    private void recordAbExposure(ExecuteResult result, String sessionId) {
        io.lumina.agent.service.AbTestService.VariantContext variant = abVariantHolder.get();
        if (variant != null && abTestService != null) {
            try {
                Integer tokens = result.getTokenUsage() != null ? result.getTokenUsage().getTotalTokens() : 0;
                abTestService.recordExposure(
                        variant.experimentId(),
                        variant.variantId(),
                        variant.variantName(),
                        sessionId,
                        result.getSuccess(),
                        result.getDuration() != null ? result.getDuration() : 0,
                        tokens,
                        result.getError());
            } catch (Exception e) {
                log.debug("A/B 曝光记录失败（不影响结果）: {}", e.getMessage());
            } finally {
                abVariantHolder.remove();
            }
        }
    }

    /**
     * 记录同步执行的 token 用量到 lumina_agent_task 表
     *
     * <p>同步执行（POST /agents/{id}/execute）不经过 AgentTaskService，
     * 但成本仪表盘和预算控制只读 task 表。此处补插一条 COMPLETED 记录，
     * 确保 sync 执行的 token 用量也能被统计。
     */
    private void recordSyncTask(Long agentId, ExecuteResult result) {
        try {
            ExecuteResult.TokenUsage usage = result.getTokenUsage();
            // 从 Agent llmConfig 解析 modelName/provider，确保成本按正确价格计算
            String modelName = "default";
            String provider = "default";
            io.lumina.agent.infrastructure.entity.AgentDO agentDO = agentMapper.selectById(agentId);
            if (agentDO != null && agentDO.getLlmConfig() != null && !agentDO.getLlmConfig().isBlank()) {
                try {
                    var config = objectMapper.readTree(agentDO.getLlmConfig());
                    if (config.has("modelName")) modelName = config.get("modelName").asText();
                    if (config.has("modelType")) provider = config.get("modelType").asText();
                } catch (Exception ignored) { }
            }
            io.lumina.agent.infrastructure.entity.AgentTaskDO task = new io.lumina.agent.infrastructure.entity.AgentTaskDO();
            task.setTaskUuid(java.util.UUID.randomUUID().toString());
            task.setAgentId(agentId);
            task.setInputText("(sync)");
            task.setStatus("COMPLETED");
            task.setResult(null);
            task.setDurationMs(result.getDuration());
            task.setModelName(modelName);
            task.setProvider(provider);
            task.setPromptTokens(usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0);
            task.setCompletionTokens(usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0);
            task.setTotalTokens(usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : 0);
            task.setTenantId(currentTenantId());
            task.setCreateBy(BaseContext.getUserId());
            task.setCreateTime(java.time.LocalDateTime.now());
            task.setUpdateTime(java.time.LocalDateTime.now());
            task.setIsDeleted(0);
            agentTaskMapper.insert(task);
        } catch (Exception e) {
            log.warn("记录同步执行 token 用量失败(不影响主流程): agentId={}, error={}", agentId, e.getMessage());
        }
    }

    /**
     * 异步触发反思记忆提取（不阻塞用户响应）
     */
    private void triggerReflectiveMemory(Long agentId, String sessionId, String userMsg, String assistantReply) {
        if (reflectiveMemoryService == null || sessionId == null) {
            return;
        }
        try {
            Long userId = BaseContext.getUserId();
            agentTaskExecutor.execute(() -> {
                try {
                    reflectiveMemoryService.extractAndSave(userId, agentId, sessionId, userMsg, assistantReply);
                } catch (Exception e) {
                    log.debug("反思记忆提取失败（不影响主流程）: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            log.debug("反思记忆异步提交失败: {}", e.getMessage());
        }
    }
}
