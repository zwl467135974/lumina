package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("lumina_knowledge_document")
public class KnowledgeDocumentDO implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "document_id", type = IdType.AUTO)
    private Long documentId;
    @TableField("document_uuid")
    private String documentUuid;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("agent_id")
    private Long agentId;
    @TableField("title")
    private String title;
    @TableField("format")
    private String format;
    @TableField("chunk_count")
    private Integer chunkCount;
    @TableField("file_size")
    private Long fileSize;
    @TableField("status")
    private Integer status;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
