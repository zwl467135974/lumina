package io.lumina.base.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OAuth2 三方身份绑定 DO（一个用户可绑定多个三方身份）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
@TableName("lumina_oauth2_identity")
public class OAuth2IdentityDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 提供商标识（github / oidc / 自定义名） */
    private String provider;

    /** 提供方用户唯一标识（github id / oidc sub） */
    private String openId;

    /** 提供方用户名（展示/日志用） */
    private String username;

    private String avatar;

    private String email;

    /** 原始 userinfo JSON（截断存储，排查用） */
    private String rawUserinfo;

    private Long tenantId;

    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer isDeleted;
}
