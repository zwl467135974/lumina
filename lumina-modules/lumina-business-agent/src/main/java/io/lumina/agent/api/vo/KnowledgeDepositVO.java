package io.lumina.agent.api.vo;

import io.lumina.agent.infrastructure.entity.KnowledgeDepositDO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识沉淀 VO
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
public class KnowledgeDepositVO {

    private Long id;
    private String title;
    private String content;
    /** TASK / MANUAL */
    private String sourceType;
    private String sourceId;
    private Long agentId;
    private Long kbId;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private String reviewComment;
    /** 审核通过后入库生成的文档 uuid（回链文档列表） */
    private String docUuid;
    private Long reviewedBy;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;

    public static KnowledgeDepositVO from(KnowledgeDepositDO deposit) {
        KnowledgeDepositVO vo = new KnowledgeDepositVO();
        vo.setId(deposit.getId());
        vo.setTitle(deposit.getTitle());
        vo.setContent(deposit.getContent());
        vo.setSourceType(deposit.getSourceType());
        vo.setSourceId(deposit.getSourceId());
        vo.setAgentId(deposit.getAgentId());
        vo.setKbId(deposit.getKbId());
        vo.setStatus(deposit.getStatus());
        vo.setReviewComment(deposit.getReviewComment());
        vo.setDocUuid(deposit.getDocUuid());
        vo.setReviewedBy(deposit.getReviewedBy());
        vo.setReviewTime(deposit.getReviewTime());
        vo.setCreateTime(deposit.getCreateTime());
        return vo;
    }
}
