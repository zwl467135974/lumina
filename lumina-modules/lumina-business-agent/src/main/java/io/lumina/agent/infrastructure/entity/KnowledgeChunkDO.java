package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库 chunk 原文 DO（混合检索关键词路数据源）
 *
 * <p>入库时双写：Qdrant（向量）+ lumina_knowledge_chunk（全文检索）。
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Data
@TableName("lumina_knowledge_chunk")
public class KnowledgeChunkDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("chunk_id")
    private String chunkId;

    @TableField("doc_uuid")
    private String docUuid;

    @TableField("kb_id")
    private Long kbId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("content")
    private String content;

    @TableField("chunk_index")
    private Integer chunkIndex;

    @TableField("vector_doc_id")
    private String vectorDocId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
