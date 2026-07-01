package io.lumina.agent.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话 VO
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Data
public class ConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String conversationUuid;
    private Long agentId;
    private String title;
    private Integer messageCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
