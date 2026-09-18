package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.lumina.agent.api.dto.AgentTaskRequestDTO;
import io.lumina.agent.api.dto.BatchTaskDTO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.AgentTaskBatchService;
import io.lumina.agent.service.AgentTaskService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.LoginContext;
import io.lumina.common.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 大输入分治服务实现
 *
 * <p>子任务经 {@link AgentTaskService#submitTask} 走完整异步管线（Agent 并发
 * 限制天然约束同时执行的子任务数）；父任务由单线程终态监视器轮询合并
 * （2s 间隔 / 30 分钟超时），服务重启时父任务由既有的重启对账机制标记
 * INTERRUPTED（结果未知 ≠ 失败）。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskBatchServiceImpl implements AgentTaskBatchService {

    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    /** 终态监视轮询间隔 */
    private static final long POLL_INTERVAL_MS = 2000;

    /** 批次总超时（超过后父任务置 FAILED） */
    private static final long BATCH_TIMEOUT_MS = 30 * 60 * 1000L;

    /** LLM 合并模式下单片结果截断（防合并 prompt 膨胀） */
    private static final int MERGE_RESULT_MAX_CHARS = 4000;

    private final AgentTaskMapper agentTaskMapper;
    private final AgentTaskService agentTaskService;
    private final AgentService agentService;

    @Value("${lumina.agent.batch.merge-max-chars:20000}")
    private int mergeMaxChars;

    /** 单线程终态监视器（守护线程，不阻塞停机） */
    private final ExecutorService finalizer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "agent-batch-finalizer");
        t.setDaemon(true);
        return t;
    });

    @Override
    public AgentTaskDO submitBatch(BatchTaskDTO dto) {
        Agent agent = agentService.getAgentById(dto.getAgentId());
        if (!agent.isActive()) {
            throw new BusinessException(ErrorCode.AGENT_NOT_ACTIVE);
        }

        List<String> bundles = split(dto);
        int bundleCount = bundles.size();
        if (bundleCount > dto.getMaxBundles()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "分片数 " + bundleCount + " 超过上限 " + dto.getMaxBundles() + "（调大 maxBundles 或增(bundleSize)）");
        }

        // 父任务（批次锚点，终态由监视器写入）
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        AgentTaskDO parent = new AgentTaskDO();
        parent.setTaskUuid(UUID.randomUUID().toString());
        parent.setAgentId(dto.getAgentId());
        parent.setStatus(STATUS_QUEUED);
        parent.setInputText("[BATCH x" + bundleCount + "] " + dto.getInstruction()
                + "\n---\n" + truncate(dto.getInputText(), 500));
        parent.setBundleCount(bundleCount);
        parent.setPromptTokens(0);
        parent.setCompletionTokens(0);
        parent.setTotalTokens(0);
        parent.setTenantId(tenantId);
        parent.setCreateBy(BaseContext.getUserId());
        parent.setCreateTime(LocalDateTime.now());
        parent.setUpdateTime(LocalDateTime.now());
        parent.setIsDeleted(0);
        agentTaskMapper.insert(parent);

        // 子任务：走完整异步管线（隔离会话），回填批次标记
        LoginContext loginContext = BaseContext.current();
        for (int i = 0; i < bundleCount; i++) {
            AgentTaskRequestDTO child = new AgentTaskRequestDTO();
            child.setTask(dto.getInstruction()
                    + "\n\n--- 以下是需要处理的第 " + (i + 1) + "/" + bundleCount + " 片内容 ---\n"
                    + bundles.get(i));
            AgentTaskDO submitted = agentTaskService.submitTask(dto.getAgentId(), child);
            agentTaskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentTaskDO>()
                    .eq(AgentTaskDO::getTaskUuid, submitted.getTaskUuid())
                    .set(AgentTaskDO::getParentUuid, parent.getTaskUuid())
                    .set(AgentTaskDO::getBundleIndex, i)
                    .set(AgentTaskDO::getBundleCount, bundleCount));
        }

        // 终态监视 + 合并（后台）
        String parentUuid = parent.getTaskUuid();
        Long agentId = dto.getAgentId();
        boolean llmMerge = "LLM".equalsIgnoreCase(dto.getMergeMode());
        String instruction = dto.getInstruction();
        finalizer.execute(() -> watchAndMerge(parentUuid, agentId, instruction, llmMerge, loginContext, tenantId));

        log.info("分治批次已提交: parent={}, agentId={}, bundles={}, mergeMode={}",
                parentUuid, agentId, bundleCount, dto.getMergeMode());
        return parent;
    }

    @Override
    public List<AgentTaskDO> listChildren(String parentUuid) {
        return agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTaskDO>()
                .eq(AgentTaskDO::getParentUuid, parentUuid)
                .eq(AgentTaskDO::getIsDeleted, 0)
                .orderByAsc(AgentTaskDO::getBundleIndex));
    }

    @Override
    public AgentTaskDO cancelBatch(String parentUuid) {
        AgentTaskDO parent = agentTaskService.getTask(parentUuid);
        if (parent.getBundleCount() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该任务不是分治批次父任务");
        }
        if (!STATUS_QUEUED.equals(parent.getStatus()) && !STATUS_RUNNING.equals(parent.getStatus())) {
            return parent;
        }
        for (AgentTaskDO child : listChildren(parentUuid)) {
            if (STATUS_QUEUED.equals(child.getStatus()) || STATUS_RUNNING.equals(child.getStatus())) {
                try {
                    agentTaskService.cancelTask(child.getTaskUuid());
                } catch (Exception e) {
                    log.warn("取消子任务失败（继续）: {}, {}", child.getTaskUuid(), e.getMessage());
                }
            }
        }
        return agentTaskService.cancelTask(parentUuid);
    }

    // ==================== 终态监视与合并 ====================

    private void watchAndMerge(String parentUuid, Long agentId, String instruction,
                               boolean llmMerge, LoginContext loginContext, Long tenantId) {
        long deadline = System.currentTimeMillis() + BATCH_TIMEOUT_MS;
        try {
            while (true) {
                AgentTaskDO parent = agentTaskService.getTask(parentUuid);
                if (STATUS_CANCELLED.equals(parent.getStatus()) || "INTERRUPTED".equals(parent.getStatus())) {
                    return; // 用户取消 / 重启对账接管
                }
                List<AgentTaskDO> children = listChildren(parentUuid);
                if (children.stream().allMatch(this::isTerminal)) {
                    mergeAndComplete(parentUuid, agentId, instruction, llmMerge, children, loginContext, tenantId);
                    return;
                }
                if (System.currentTimeMillis() > deadline) {
                    failParent(parentUuid, "批次超时（" + (BATCH_TIMEOUT_MS / 60000) + " 分钟）未全部完成");
                    return;
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("分治批次监视失败: parent={}, {}", parentUuid, e.getMessage());
            failParent(parentUuid, "批次监视异常: " + e.getMessage());
        }
    }

    private void mergeAndComplete(String parentUuid, Long agentId, String instruction, boolean llmMerge,
                                  List<AgentTaskDO> children, LoginContext loginContext, Long tenantId) {
        List<AgentTaskDO> ok = children.stream()
                .filter(c -> STATUS_COMPLETED.equals(c.getStatus())).toList();
        List<Integer> failedIndexes = children.stream()
                .filter(c -> !STATUS_COMPLETED.equals(c.getStatus()))
                .map(AgentTaskDO::getBundleIndex).toList();

        String merged = concatResults(instruction, ok, children.size());
        if (llmMerge && !ok.isEmpty()) {
            merged = llmMerge(agentId, instruction, ok, loginContext, tenantId, merged);
        }

        int totalTokens = children.stream()
                .mapToInt(c -> c.getTotalTokens() != null ? c.getTotalTokens() : 0).sum();

        if (!failedIndexes.isEmpty()) {
            failParent(parentUuid, "部分分片未完成（序号 " + failedIndexes + "），以下为成功分片合并结果", merged, totalTokens);
            return;
        }
        agentTaskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentTaskDO>()
                .eq(AgentTaskDO::getTaskUuid, parentUuid)
                .set(AgentTaskDO::getStatus, STATUS_COMPLETED)
                .set(AgentTaskDO::getResult, merged)
                .set(AgentTaskDO::getTotalTokens, totalTokens)
                .set(AgentTaskDO::getUpdateTime, LocalDateTime.now()));
        log.info("分治批次完成: parent={}, children={}, tokens={}", parentUuid, children.size(), totalTokens);
    }

    /** 工程拼接：带分片标头，按序号 */
    static String concatResults(String instruction, List<AgentTaskDO> okChildren, int totalCount) {
        StringBuilder sb = new StringBuilder();
        for (AgentTaskDO child : okChildren) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("=== 分片 ").append((child.getBundleIndex() != null ? child.getBundleIndex() : 0) + 1)
                    .append('/').append(totalCount).append(" ===\n")
                    .append(child.getResult() != null ? child.getResult() : "");
        }
        return sb.toString();
    }

    /** LLM 汇总（单片截断 + 总长上限；失败回退工程拼接） */
    private String llmMerge(Long agentId, String instruction, List<AgentTaskDO> okChildren,
                             LoginContext loginContext, Long tenantId, String fallback) {
        try {
            applyContext(loginContext, tenantId);
            StringBuilder sb = new StringBuilder("以下是按序号排列的各分片处理结果，请汇总为一份完整、去重的最终结果：\n");
            for (AgentTaskDO child : okChildren) {
                sb.append("\n[分片 ").append((child.getBundleIndex() != null ? child.getBundleIndex() : 0) + 1).append("]\n")
                        .append(truncate(child.getResult() != null ? child.getResult() : "", MERGE_RESULT_MAX_CHARS))
                        .append('\n');
                if (sb.length() > mergeMaxChars) {
                    break;
                }
            }
            ExecuteResult result = agentService.executeAgentForResult(agentId, sb.toString(), null);
            return result.getResult() != null ? result.getResult() : fallback;
        } catch (Exception e) {
            log.warn("LLM 合并失败，回退工程拼接: parent 批次, {}", e.getMessage());
            return fallback;
        } finally {
            BaseContext.clear();
        }
    }

    private void failParent(String parentUuid, String message) {
        failParent(parentUuid, message, null, 0);
    }

    private void failParent(String parentUuid, String message, String partialResult, int totalTokens) {
        agentTaskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentTaskDO>()
                .eq(AgentTaskDO::getTaskUuid, parentUuid)
                .set(AgentTaskDO::getStatus, STATUS_FAILED)
                .set(AgentTaskDO::getErrorMessage, message)
                .set(partialResult != null, AgentTaskDO::getResult, partialResult)
                .set(AgentTaskDO::getTotalTokens, totalTokens)
                .set(AgentTaskDO::getUpdateTime, LocalDateTime.now()));
    }

    private boolean isTerminal(AgentTaskDO task) {
        return STATUS_COMPLETED.equals(task.getStatus()) || STATUS_FAILED.equals(task.getStatus())
                || STATUS_CANCELLED.equals(task.getStatus()) || "INTERRUPTED".equals(task.getStatus());
    }

    private static void applyContext(LoginContext loginContext, Long tenantId) {
        BaseContext.setTenantId(tenantId);
        if (loginContext != null) {
            BaseContext.setUserId(loginContext.userId());
            BaseContext.setUsername(loginContext.username());
        }
    }

    // ==================== 确定性拆分（工程代码，可测） ====================

    /** 按策略拆分（package-private 供单测） */
    static List<String> split(BatchTaskDTO dto) {
        return "BY_CHARS".equalsIgnoreCase(dto.getSplitStrategy())
                ? splitByChars(dto.getInputText(), dto.getBundleSize())
                : splitByLines(dto.getInputText(), dto.getBundleSize());
    }

    /** 按行分组：每 bundleSize 行一片（空行不切散语义） */
    static List<String> splitByLines(String text, int linesPerBundle) {
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        List<String> bundles = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (count > 0 && count % linesPerBundle == 0) {
                bundles.add(current.toString().stripTrailing());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
            count++;
        }
        if (!current.toString().isBlank()) {
            bundles.add(current.toString().stripTrailing());
        }
        return bundles;
    }

    /** 按字符切分：相邻片 10% 重叠（减少语义断裂），空片丢弃 */
    static List<String> splitByChars(String text, int charsPerBundle) {
        List<String> bundles = new ArrayList<>();
        int overlap = Math.max(1, charsPerBundle / 10);
        int offset = 0;
        while (offset < text.length()) {
            int end = Math.min(offset + charsPerBundle, text.length());
            String piece = text.substring(offset, end).strip();
            if (!piece.isEmpty()) {
                bundles.add(piece);
            }
            if (end >= text.length()) {
                break;
            }
            offset = end - overlap;
        }
        return bundles;
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) + "…" : value;
    }

    @PreDestroy
    void shutdown() {
        finalizer.shutdownNow();
    }
}
