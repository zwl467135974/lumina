package io.lumina.agent.service;

import io.lumina.agent.api.dto.BatchTaskDTO;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;

import java.util.List;

/**
 * 大输入分治服务（fan-out 批次任务）
 *
 * <p>借鉴 open-code-review 分治设计：工程代码确定性拆分（bundle）→ 子任务
 * 隔离上下文并发执行（复用完整执行管线：限流/预算/审计/取消）→ 全部终态后
 * 合并（CONCAT 工程拼接 / LLM 汇总）写入父任务。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
public interface AgentTaskBatchService {

    /**
     * 提交分治批次：拆分 → 建父任务 → 提交 N 个子任务（带 parentUuid 标记）
     * → 后台终态监视器合并写父任务
     *
     * @return 父任务（QUEUED）
     */
    AgentTaskDO submitBatch(BatchTaskDTO dto);

    /** 按父任务查子任务（按 bundleIndex 升序） */
    List<AgentTaskDO> listChildren(String parentUuid);

    /** 取消整批：父任务置 CANCELLED，逐个取消未终态子任务 */
    AgentTaskDO cancelBatch(String parentUuid);
}
