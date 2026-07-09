package io.lumina.base.api.vo.user;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线用户 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class OnlineUserVO {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 登录时间
     */
    private LocalDateTime loginTime;
}
