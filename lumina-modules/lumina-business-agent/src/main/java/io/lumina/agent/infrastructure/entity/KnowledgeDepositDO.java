package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识沉淀 DO（任务产物 → 人工审核 → 入知识库）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
@TableName("lumina_knowledge_deposit")
public class KnowledgeDepositDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /** 来源：TASK / MANUAL */
    private String sourceType;

    /** 来源标识（taskUuid / conversationUuid） */
    private String sourceId;

    private Long agentId;

    /** 目标知识库 */
    private Long kbId;

    /** PENDING / APPROVED / REJECTED */
    private String status;

    private String reviewComment;

    private Long reviewedBy;

    private LocalDateTime reviewTime;

    /** 审核通过入库后生成的文档 uuid（回链） */
    private String docUuid;

    private Long tenantId;

    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer isDeleted;
}
