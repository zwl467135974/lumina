package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流实例 DO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@TableName("lumina_workflow_instance")
public class WorkflowInstanceDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long definitionId;
    private String definitionName;
    private Integer definitionVersion;
    private String status;
    private String input;
    private String output;
    private String errorMessage;
    private String currentNodeId;
    private Long tenantId;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
