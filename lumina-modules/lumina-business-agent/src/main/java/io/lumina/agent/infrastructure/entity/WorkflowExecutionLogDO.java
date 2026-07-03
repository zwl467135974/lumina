package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流节点执行日志 DO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@TableName("lumina_workflow_execution_log")
public class WorkflowExecutionLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instanceId;
    private String nodeId;
    private String nodeType;
    private String nodeName;
    private String status;
    private String input;
    private String output;
    private Integer durationMs;
    private String errorMessage;
    private LocalDateTime createTime;
}
