package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Agent-知识库挂载关系 DO（E5）
 *
 * @author Lumina Team
 * @since 2.1.0
 */
@Data
@TableName("lumina_agent_knowledge_base")
public class AgentKnowledgeBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private Long kbId;
    private Long tenantId;
    private LocalDateTime createTime;
}
