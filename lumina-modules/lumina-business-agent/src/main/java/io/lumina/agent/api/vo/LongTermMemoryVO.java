package io.lumina.agent.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 长期记忆 VO
 *
 * <p>API 出参专用，隔离数据库实体 {@code LongTermMemoryDO}。
 *
 * @author Lumina Team
 * @since 3.10.0
 */
@Data
public class LongTermMemoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long agentId;
    private String conversationId;
    private String memoryType;
    private String content;
    private BigDecimal importance;
    private Integer accessCount;
    private LocalDateTime lastAccessed;
    private LocalDateTime createTime;
}
