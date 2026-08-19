package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具执行结果存档 DO
 *
 * <p>超大工具结果外存化（spill）：全文入库，模型侧只留预览 + artifactId。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Data
@TableName("lumina_tool_artifact")
public class ToolArtifactDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String artifactId;
    private String conversationId;
    private String toolName;
    private String content;
    private Integer contentChars;
    private Long tenantId;
    private Long createBy;
    private LocalDateTime createTime;
    private Integer isDeleted;
}
