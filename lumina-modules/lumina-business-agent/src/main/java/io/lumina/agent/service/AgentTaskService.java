package io.lumina.agent.service;

import io.lumina.agent.api.dto.AgentTaskRequestDTO;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.common.core.PageResult;

import java.util.Map;

/**
 * Agent 异步任务服务
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface AgentTaskService {

    /** 提交异步任务 */
    AgentTaskDO submitTask(Long agentId, AgentTaskRequestDTO dto);

    /** 查询任务详情 */
    AgentTaskDO getTask(String taskUuid);

    /**
     * 分页查询任务列表
     *
     * @param agentId  Agent ID（null 查全部）
     * @param status   状态过滤（null 查全部）
     */
    PageResult<AgentTaskDO> pageTasks(Long agentId, String status, int pageNum, int pageSize);

    /**
     * 同步执行已入库的任务（供 MQ 消费者 / 定时触发器复用完整执行管线）
     *
     * <p>内部自行 set/clear BaseContext，执行结果通过任务状态与 SSE 进度流反馈，不抛业务异常。
     */
    void executeTask(String taskUuid, io.lumina.common.core.LoginContext loginContext);

    /** 取消任务（仅 QUEUED / RUNNING 可取消） */
    AgentTaskDO cancelTask(String taskUuid);

    /** 获取任务进度流（用于 SSE 推送） */
    reactor.core.publisher.Flux<Map<String, Object>> streamTaskProgress(String taskUuid);
}
