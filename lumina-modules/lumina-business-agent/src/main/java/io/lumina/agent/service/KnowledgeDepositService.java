package io.lumina.agent.service;

import io.lumina.agent.api.dto.KnowledgeDepositDTO;
import io.lumina.agent.api.dto.KnowledgeDepositReviewDTO;
import io.lumina.agent.api.vo.KnowledgeDepositVO;
import io.lumina.common.core.PageResult;

/**
 * 知识沉淀服务（自维护 Wiki 知识飞轮）
 *
 * <p>闭环：任务产物/手动内容 → 沉淀队列（PENDING）→ 人工审核
 * （通过 = 复用 RAG 入库管线写入目标知识库 / 驳回 = 留痕），
 * 知识库随使用变厚，审核是质量与安全闸门。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
public interface KnowledgeDepositService {

    /** 发起沉淀（进入待审队列） */
    KnowledgeDepositVO create(KnowledgeDepositDTO dto);

    /** 分页查询（status/kbId 可选过滤，当前租户） */
    PageResult<KnowledgeDepositVO> page(String status, Long kbId, int pageNum, int pageSize);

    /**
     * 审核：通过 → 同步入库目标知识库（记录 docUuid 回链）；驳回 → 仅留痕
     *
     * <p>仅 PENDING 可审核（幂等保护，重复审核抛冲突）。
     */
    KnowledgeDepositVO review(Long id, KnowledgeDepositReviewDTO dto);
}
