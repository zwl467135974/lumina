package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.agent.api.dto.CreateAgentTriggerDTO;
import io.lumina.agent.api.vo.AgentTriggerVO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.infrastructure.entity.AgentTriggerDO;
import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import io.lumina.agent.infrastructure.mapper.AgentTriggerMapper;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.AgentTaskService;
import io.lumina.agent.service.AgentTriggerService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.LoginContext;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.event.NotificationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Agent 定时触发器服务实现
 *
 * <p>触发链路：{@code AgentTriggerPoller (@Scheduled)} → {@link #fireDueTriggers()} →
 * Redisson 分布式锁（防多实例重复触发）→ 插入 {@code lumina_agent_task}（trigger_id 回链）→
 * {@link AgentTaskService#executeTask}（自动继承 SSE 进度/通知/状态跟踪/预算/限流/审计管线）。
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@Slf4j
@Service
public class AgentTriggerServiceImpl implements AgentTriggerService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String TASK_STATUS_QUEUED = "QUEUED";
    private static final String TASK_STATUS_FAILED = "FAILED";

    private static final String MISFIRE_FIRE_ONCE = "FIRE_ONCE";
    private static final String MISFIRE_SKIP = "SKIP";

    /**
     * Redisson 锁 key 前缀，完整格式 lumina:trigger:fire:{triggerId}
     */
    private static final String LOCK_KEY_PREFIX = "lumina:trigger:fire:";

    /**
     * 锁自动释放时间（秒），防止实例宕机死锁
     */
    private static final long LOCK_LEASE_SECONDS = 60;

    /**
     * 超过该阈值未触发视为 misfire，按 misfire_policy 处理
     */
    private static final Duration MISFIRE_THRESHOLD = Duration.ofHours(1);

    /**
     * 连续失败达到该次数自动禁用（mirror WebhookSender 模式）
     */
    private static final int MAX_FAIL_COUNT = 5;

    /**
     * 触发指标名：agent.trigger.fire{result=success|failed|skipped_misfire}
     */
    private static final String METRIC_TRIGGER_FIRE = "agent.trigger.fire";

    private static final DateTimeFormatter FIRE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentTriggerMapper agentTriggerMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentTaskService agentTaskService;
    private final AgentService agentService;
    private final RedissonClient redissonClient;
    private final NotificationEventPublisher notificationEventPublisher;
    private final Executor agentTaskExecutor;

    @Autowired(required = false)
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public AgentTriggerServiceImpl(AgentTriggerMapper agentTriggerMapper,
                                   AgentTaskMapper agentTaskMapper,
                                   AgentTaskService agentTaskService,
                                   AgentService agentService,
                                   RedissonClient redissonClient,
                                   NotificationEventPublisher notificationEventPublisher,
                                   @Qualifier("agentTaskExecutor") Executor agentTaskExecutor) {
        this.agentTriggerMapper = agentTriggerMapper;
        this.agentTaskMapper = agentTaskMapper;
        this.agentTaskService = agentTaskService;
        this.agentService = agentService;
        this.redissonClient = redissonClient;
        this.notificationEventPublisher = notificationEventPublisher;
        this.agentTaskExecutor = agentTaskExecutor;
    }

    // ==================== CRUD ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentTriggerVO createTrigger(CreateAgentTriggerDTO dto) {
        CronExpression cron = parseCron(dto.getCronExpr());

        // 创建阶段校验目标 Agent 存在且启用，避免无效触发器空转
        Agent agent = agentService.getAgentById(dto.getAgentId());
        if (!agent.isActive()) {
            throw new BusinessException(ErrorCode.AGENT_NOT_ACTIVE);
        }

        AgentTriggerDO trigger = new AgentTriggerDO();
        trigger.setName(dto.getName());
        trigger.setAgentId(dto.getAgentId());
        trigger.setWorkflowId(dto.getWorkflowId());
        trigger.setCronExpr(dto.getCronExpr());
        trigger.setInputText(dto.getInputText());
        trigger.setMisfirePolicy(StringUtils.hasText(dto.getMisfirePolicy())
                ? dto.getMisfirePolicy() : MISFIRE_FIRE_ONCE);
        trigger.setEnabled(1);
        trigger.setNextFireAt(cron.next(LocalDateTime.now()));
        trigger.setFailCount(0);
        trigger.setTenantId(currentTenant());
        trigger.setCreateBy(BaseContext.getUserId());
        trigger.setCreateTime(LocalDateTime.now());
        trigger.setUpdateTime(LocalDateTime.now());
        trigger.setIsDeleted(0);
        agentTriggerMapper.insert(trigger);

        log.info("定时触发器已创建: id={}, name={}, agentId={}, cron={}, nextFireAt={}",
                trigger.getId(), trigger.getName(), trigger.getAgentId(), trigger.getCronExpr(), trigger.getNextFireAt());
        return toVO(trigger);
    }

    @Override
    public AgentTriggerVO getTrigger(Long id) {
        return toVO(getOwnedTrigger(id));
    }

    @Override
    public PageResult<AgentTriggerVO> pageTriggers(int pageNum, int pageSize) {
        LambdaQueryWrapper<AgentTriggerDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTriggerDO::getIsDeleted, 0);
        wrapper.orderByDesc(AgentTriggerDO::getCreateTime);

        Page<AgentTriggerDO> page = agentTriggerMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<AgentTriggerVO> list = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, page.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTrigger(Long id) {
        AgentTriggerDO trigger = getOwnedTrigger(id);
        trigger.setIsDeleted(1);
        trigger.setUpdateTime(LocalDateTime.now());
        agentTriggerMapper.updateById(trigger);
        log.info("定时触发器已删除: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pause(Long id) {
        AgentTriggerDO trigger = getOwnedTrigger(id);
        trigger.setEnabled(0);
        trigger.setUpdateTime(LocalDateTime.now());
        agentTriggerMapper.updateById(trigger);
        log.info("定时触发器已暂停: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long id) {
        AgentTriggerDO trigger = getOwnedTrigger(id);
        CronExpression cron = parseCron(trigger.getCronExpr());
        trigger.setEnabled(1);
        // 从当前时间重算，避免恢复后立即补触发历史积压
        trigger.setNextFireAt(cron.next(LocalDateTime.now()));
        trigger.setFailCount(0);
        trigger.setUpdateTime(LocalDateTime.now());
        agentTriggerMapper.updateById(trigger);
        log.info("定时触发器已恢复: id={}, nextFireAt={}", id, trigger.getNextFireAt());
    }

    @Override
    public boolean triggerNow(Long id) {
        AgentTriggerDO trigger = getOwnedTrigger(id);
        // 手动触发单次执行，不走 Redisson 锁；异步执行避免阻塞 HTTP 线程
        agentTaskExecutor.execute(() -> fireInternal(trigger));
        log.info("定时触发器手动触发: id={}, agentId={}", id, trigger.getAgentId());
        return true;
    }

    // ==================== 定时触发 ====================

    @Override
    public void fireDueTriggers() {
        List<AgentTriggerDO> dueTriggers = agentTriggerMapper.selectDueTriggers();
        if (dueTriggers.isEmpty()) {
            return;
        }
        log.info("定时触发器扫描: {} 个到期", dueTriggers.size());
        for (AgentTriggerDO trigger : dueTriggers) {
            try {
                fireWithLock(trigger);
            } catch (Exception e) {
                log.error("定时触发器处理异常: id={}", trigger.getId(), e);
            }
        }
    }

    /**
     * misfire 判定 + Redisson 分布式锁保护下触发单个 trigger
     */
    private void fireWithLock(AgentTriggerDO trigger) {
        // misfire：错过超过阈值且策略为 SKIP，仅前进 next_fire_at 不触发
        if (isMisfired(trigger) && MISFIRE_SKIP.equals(trigger.getMisfirePolicy())) {
            LocalDateTime next = parseCron(trigger.getCronExpr()).next(LocalDateTime.now());
            agentTriggerMapper.updateNextFireAt(trigger.getId(), next);
            recordMetric("skipped_misfire");
            log.info("定时触发器 misfire 跳过: id={}, 原定 {}, 前进至 {}",
                    trigger.getId(), trigger.getNextFireAt(), next);
            return;
        }

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + trigger.getId());
        boolean acquired;
        try {
            // 非阻塞获取，60s 自动释放；获取失败=另一实例正在处理
            acquired = lock.tryLock(0, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!acquired) {
            log.debug("定时触发器 {} 已被其他实例处理，跳过", trigger.getId());
            return;
        }
        try {
            fireInternal(trigger);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 触发核心：system 上下文下插入 agent_task 并复用 executeTask 完整管线
     */
    private void fireInternal(AgentTriggerDO trigger) {
        // system 上下文使用 trigger 归属租户，保证任务与 Agent 查询落在正确租户
        LoginContext systemContext = new LoginContext(
                trigger.getTenantId(), 0L, "system", new String[]{"SYSTEM"}, null);
        BaseContext.setCurrent(systemContext);
        try {
            AgentTaskDO task = new AgentTaskDO();
            task.setTaskUuid(UUID.randomUUID().toString());
            task.setAgentId(trigger.getAgentId());
            task.setTriggerId(trigger.getId());
            task.setInputText(trigger.getInputText());
            task.setStatus(TASK_STATUS_QUEUED);
            task.setPromptTokens(0);
            task.setCompletionTokens(0);
            task.setTotalTokens(0);
            task.setTenantId(trigger.getTenantId());
            task.setCreateBy(trigger.getCreateBy());
            task.setCreateTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            task.setIsDeleted(0);
            agentTaskMapper.insert(task);

            // 同步执行：自动继承 SSE 进度/NotificationEvent/状态跟踪/预算/限流/审计
            agentTaskService.executeTask(task.getTaskUuid(), systemContext);

            // executeTask 内部 finally 会清空上下文，恢复后再查询任务终态
            BaseContext.setCurrent(systemContext);
            AgentTaskDO finished = selectTaskByUuid(task.getTaskUuid());
            if (finished != null && TASK_STATUS_FAILED.equals(finished.getStatus())) {
                onFireFailure(trigger, finished.getErrorMessage());
            } else {
                onFireSuccess(trigger);
            }
        } catch (Exception e) {
            log.error("定时触发器触发失败: id={}", trigger.getId(), e);
            onFireFailure(trigger, e.getMessage());
        } finally {
            BaseContext.clear();
        }
    }

    private void onFireSuccess(AgentTriggerDO trigger) {
        LocalDateTime next = nextFireAtSafe(trigger);
        agentTriggerMapper.updateFired(trigger.getId(), STATUS_SUCCESS, next);
        recordMetric("success");
        log.info("定时触发器触发成功: id={}, nextFireAt={}", trigger.getId(), next);
    }

    private void onFireFailure(AgentTriggerDO trigger, String errorMessage) {
        int failCount = (trigger.getFailCount() != null ? trigger.getFailCount() : 0) + 1;
        boolean disable = failCount >= MAX_FAIL_COUNT;
        String error = truncate(errorMessage, 512);
        LocalDateTime next = nextFireAtSafe(trigger);
        agentTriggerMapper.updateFireFailed(trigger.getId(), failCount, error, next, disable ? 0 : 1);
        recordMetric("failed");
        log.warn("定时触发器触发失败: id={}, failCount={}, disabled={}, error={}",
                trigger.getId(), failCount, disable, error);

        if (disable) {
            try {
                notificationEventPublisher.publish(new NotificationEvent(
                        trigger.getCreateBy(), "TRIGGER",
                        "定时触发器已禁用: " + trigger.getName(),
                        "连续失败 " + failCount + " 次已自动禁用，最近错误: " + error,
                        "ERROR", "agent_trigger", String.valueOf(trigger.getId()), trigger.getTenantId()));
            } catch (Exception ex) {
                log.warn("发送触发器禁用通知失败(不影响主流程): {}", ex.getMessage());
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 校验并解析 cron 表达式（Spring 6 字段：秒 分 时 日 月 周）
     */
    private CronExpression parseCron(String cronExpr) {
        try {
            return CronExpression.parse(cronExpr);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "cron 表达式非法（需 Spring 6 字段格式：秒 分 时 日 月 周）: " + e.getMessage());
        }
    }

    /**
     * 计算下次触发时间；表达式损坏时返回 null（不再调度）
     */
    private LocalDateTime nextFireAtSafe(AgentTriggerDO trigger) {
        try {
            return CronExpression.parse(trigger.getCronExpr()).next(LocalDateTime.now());
        } catch (IllegalArgumentException e) {
            log.error("定时触发器 cron 表达式损坏: id={}, cron={}", trigger.getId(), trigger.getCronExpr());
            return null;
        }
    }

    private boolean isMisfired(AgentTriggerDO trigger) {
        return trigger.getNextFireAt() != null
                && trigger.getNextFireAt().isBefore(LocalDateTime.now().minus(MISFIRE_THRESHOLD));
    }

    /**
     * 查询并校验触发器归属当前租户（租户插件自动过滤）
     */
    private AgentTriggerDO getOwnedTrigger(Long id) {
        AgentTriggerDO trigger = agentTriggerMapper.selectById(id);
        if (trigger == null || Integer.valueOf(1).equals(trigger.getIsDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "定时触发器不存在: " + id);
        }
        return trigger;
    }

    private AgentTaskDO selectTaskByUuid(String taskUuid) {
        LambdaQueryWrapper<AgentTaskDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTaskDO::getTaskUuid, taskUuid);
        wrapper.eq(AgentTaskDO::getIsDeleted, 0);
        wrapper.last("LIMIT 1");
        return agentTaskMapper.selectOne(wrapper);
    }

    private void recordMetric(String result) {
        if (meterRegistry != null) {
            meterRegistry.counter(METRIC_TRIGGER_FIRE, "result", result).increment();
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private Long currentTenant() {
        return BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
    }

    private AgentTriggerVO toVO(AgentTriggerDO trigger) {
        AgentTriggerVO vo = new AgentTriggerVO();
        BeanUtils.copyProperties(trigger, vo);
        if (trigger.getNextFireAt() != null && Integer.valueOf(1).equals(trigger.getEnabled())) {
            vo.setNextFireAtDescription("下次：" + FIRE_TIME_FORMATTER.format(trigger.getNextFireAt()));
        }
        return vo;
    }
}
