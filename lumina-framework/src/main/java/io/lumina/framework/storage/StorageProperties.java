package io.lumina.framework.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置属性
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "lumina.storage")
public class StorageProperties {

    /**
     * 存储类型：local / minio
     */
    private String type = "local";

    /**
     * 本地存储配置
     */
    private LocalConfig local = new LocalConfig();

    /**
     * MinIO 配置
     */
    private MinioConfig minio = new MinioConfig();

    @Data
    public static class LocalConfig {
        /** 本地存储根目录 */
        private String basePath = "./data/files";
        /** URL 访问前缀 */
        private String urlPrefix = "/files";
    }

    @Data
    public static class MinioConfig {
        /** MinIO 服务地址 */
        private String endpoint = "http://localhost:9000";
        /** 访问密钥 */
        private String accessKey = "minioadmin";
        /** 秘密密钥 */
        private String secretKey = "minioadmin";
        /** 存储桶名称 */
        private String bucket = "lumina-files";
    }
}
