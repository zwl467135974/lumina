package io.lumina.framework.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * MinIO 对象存储实现
 *
 * <p>生产环境使用，S3 兼容协议。可无缝切换到阿里云 OSS / 腾讯云 COS（改 endpoint 即可）。
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.storage", name = "type", havingValue = "minio")
public class MinioStorageClient implements StorageClient {

    private final StorageProperties properties;
    private MinioClient minioClient;

    public MinioStorageClient(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        StorageProperties.MinioConfig cfg = properties.getMinio();
        minioClient = MinioClient.builder()
                .endpoint(cfg.getEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .build();

        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(cfg.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(cfg.getBucket()).build());
                log.info("MinIO bucket 已创建: {}", cfg.getBucket());
            }
            log.info("MinIO 存储初始化: endpoint={}, bucket={}", cfg.getEndpoint(), cfg.getBucket());
        } catch (Exception e) {
            throw new RuntimeException("MinIO 初始化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String upload(InputStream stream, String key, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(key)
                    .stream(stream, -1, 10 * 1024 * 1024)
                    .contentType(contentType)
                    .build());
            log.debug("文件已上传到 MinIO: key={}", key);
            return key;
        } catch (Exception e) {
            throw new RuntimeException("MinIO 上传失败: " + key, e);
        }
    }

    @Override
    public InputStream download(String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("MinIO 下载失败: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(key)
                    .build());
            log.debug("MinIO 文件已删除: key={}", key);
        } catch (Exception e) {
            log.warn("MinIO 删除失败: key={}, error={}", key, e.getMessage());
        }
    }

    @Override
    public String getUrl(String key) {
        return properties.getMinio().getEndpoint() + "/" + properties.getMinio().getBucket() + "/" + key;
    }
}
