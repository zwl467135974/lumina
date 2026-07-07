package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识库 DO（E5 知识库联邦）
 *
 * @author Lumina Team
 * @since 2.1.0
 */
@Data
@TableName("lumina_knowledge_base")
public class KnowledgeBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    /** 可见性：PRIVATE / TEAM / PUBLIC */
    private String visibility;
    private Long tenantId;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField("is_deleted")
    private Integer isDeleted;
}
