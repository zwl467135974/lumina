package io.lumina.framework.storage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件元数据 DO
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Data
@TableName("lumina_file")
public class FileDO {

    @TableId(type = IdType.AUTO)
    private Long fileId;

    private String fileUuid;

    private String originalName;

    private String contentType;

    private Long fileSize;

    private String storageKey;

    private String storageType;

    private String fileUrl;

    private String md5Hash;

    private Long tenantId;

    private String bizType;

    private String bizRefId;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
