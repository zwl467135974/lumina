package io.lumina.framework.storage;

import lombok.Data;

/**
 * 文件上传响应 VO
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Data
public class FileVO {

    private String fileUuid;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String url;
}
