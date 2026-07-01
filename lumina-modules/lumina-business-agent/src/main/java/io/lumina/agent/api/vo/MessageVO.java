package io.lumina.agent.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话消息 VO
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Data
public class MessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long messageId;
    private String role;
    private String content;
    private Integer tokenCount;
    private Long durationMs;
    private LocalDateTime createTime;
}
