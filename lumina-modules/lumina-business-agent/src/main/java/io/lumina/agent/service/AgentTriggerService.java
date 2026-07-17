package io.lumina.agent.service;

import io.lumina.agent.api.dto.CreateAgentTriggerDTO;
import io.lumina.agent.api.vo.AgentTriggerVO;
import io.lumina.common.core.PageResult;

/**
 * Agent 定时触发器服务
 *
 * <p>按 cron 表达式（Spring 6 字段：秒 分 时 日 月 周）定时向目标 Agent 提交异步任务，
 * 复用 {@link AgentTaskService#executeTask} 完整执行管线（SSE 进度/通知/预算/限流/审计）。
 *
 * @author Lumina Team
 * @since 3.5.0
 */
public interface AgentTriggerService {

    /** 创建触发器（校验 cron 合法性与目标 Agent 可用性） */
    AgentTriggerVO createTrigger(CreateAgentTriggerDTO dto);

    /** 查询触发器详情 */
    AgentTriggerVO getTrigger(Long id);

    /** 分页查询当前租户的触发器 */
    PageResult<AgentTriggerVO> pageTriggers(int pageNum, int pageSize);

    /** 删除触发器（逻辑删除） */
    void deleteTrigger(Long id);

    /** 暂停触发器（enabled=0） */
    void pause(Long id);

    /** 恢复触发器（enabled=1 并从当前时间重算 next_fire_at） */
    void resume(Long id);

    /** 手动立即触发一次（异步执行，不走分布式锁） */
    boolean triggerNow(Long id);

    /** 扫描并触发所有到时间的触发器（poller 调用，跨租户） */
    void fireDueTriggers();
}
