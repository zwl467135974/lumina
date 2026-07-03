package io.lumina.framework.storage;

import java.io.InputStream;

/**
 * 文件存储客户端接口
 *
 * <p>抽象文件上传/下载/删除操作，支持本地磁盘和 MinIO 两种实现。
 * 切换方式：{@code lumina.storage.type=local} 或 {@code lumina.storage.type=minio}
 *
 * @author Lumina Team
 * @since 1.3.0
 */
public interface StorageClient {

    /**
     * 上传文件
     *
     * @param stream      文件输入流
     * @param key         存储键（相对路径或 S3 object key）
     * @param contentType MIME 类型
     * @return 存储键（实际使用的 key）
     */
    String upload(InputStream stream, String key, String contentType);

    /**
     * 下载文件
     *
     * @param key 存储键
     * @return 文件输入流
     */
    InputStream download(String key);

    /**
     * 删除文件
     *
     * @param key 存储键
     */
    void delete(String key);

    /**
     * 获取文件访问 URL
     *
     * @param key 存储键
     * @return 访问 URL（本地=相对路径，MinIO=预签名 URL）
     */
    String getUrl(String key);

    /**
     * 上传字节数组
     *
     * @param bytes       文件内容
     * @param key         存储键
     * @param contentType MIME 类型
     * @return 存储键
     */
    default String upload(byte[] bytes, String key, String contentType) {
        return upload(new java.io.ByteArrayInputStream(bytes), key, contentType);
    }
}
