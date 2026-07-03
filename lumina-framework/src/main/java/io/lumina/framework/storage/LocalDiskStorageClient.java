package io.lumina.framework.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘文件存储实现
 *
 * <p>开发环境使用，文件存储到 {@code lumina.storage.local.base-path} 目录下。
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalDiskStorageClient implements StorageClient {

    private final StorageProperties properties;

    public LocalDiskStorageClient(StorageProperties properties) {
        this.properties = properties;
        try {
            Path basePath = Paths.get(properties.getLocal().getBasePath()).toAbsolutePath().normalize();
            Files.createDirectories(basePath);
            log.info("本地文件存储初始化: basePath={}", basePath);
        } catch (IOException e) {
            throw new RuntimeException("初始化本地存储目录失败", e);
        }
    }

    @Override
    public String upload(InputStream stream, String key, String contentType) {
        try {
            Path target = resolveKey(key);
            Files.createDirectories(target.getParent());
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("文件已存储: key={}, size={}bytes", key, Files.size(target));
            return key;
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败: " + key, e);
        }
    }

    @Override
    public InputStream download(String key) {
        try {
            Path target = resolveKey(key);
            if (!Files.exists(target)) {
                throw new RuntimeException("文件不存在: " + key);
            }
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new RuntimeException("文件下载失败: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Path target = resolveKey(key);
            Files.deleteIfExists(target);
            log.debug("文件已删除: key={}", key);
        } catch (IOException e) {
            log.warn("文件删除失败: key={}, error={}", key, e.getMessage());
        }
    }

    @Override
    public String getUrl(String key) {
        return properties.getLocal().getUrlPrefix() + "/" + key;
    }

    private Path resolveKey(String key) {
        Path basePath = Paths.get(properties.getLocal().getBasePath()).toAbsolutePath().normalize();
        Path resolved = basePath.resolve(key).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new SecurityException("非法存储路径: " + key);
        }
        return resolved;
    }
}
