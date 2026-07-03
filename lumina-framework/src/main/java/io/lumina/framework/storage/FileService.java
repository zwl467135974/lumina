package io.lumina.framework.storage;

import io.lumina.common.core.BaseContext;
import io.lumina.framework.storage.entity.FileDO;
import io.lumina.framework.storage.mapper.FileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件存储服务
 *
 * <p>统一管理文件上传/下载/删除，协调 StorageClient 和文件元数据持久化。
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Slf4j
@Service
public class FileService {

    @Autowired
    private StorageClient storageClient;

    @Autowired
    private FileMapper fileMapper;

    /**
     * 上传文件
     *
     * @param file    MultipartFile
     * @param bizType 业务类型（如 chat_image / knowledge_doc / avatar）
     * @return 文件元数据
     */
    public FileDO upload(MultipartFile file, String bizType) {
        try {
            return upload(file.getBytes(), file.getOriginalFilename(), file.getContentType(), bizType);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败", e);
        }
    }

    /**
     * 上传文件（字节数组）
     *
     * @param bytes        文件内容
     * @param originalName 原始文件名
     * @param contentType  MIME 类型
     * @param bizType      业务类型
     * @return 文件元数据
     */
    public FileDO upload(byte[] bytes, String originalName, String contentType, String bizType) {
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        String fileUuid = UUID.randomUUID().toString().replace("-", "");

        String ext = extractExtension(originalName);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String storageKey = tenantId + "/" + datePath + "/" + fileUuid + (ext.isEmpty() ? "" : "." + ext);

        storageClient.upload(bytes, storageKey, contentType);

        FileDO fileDO = new FileDO();
        fileDO.setFileUuid(fileUuid);
        fileDO.setOriginalName(originalName);
        fileDO.setContentType(contentType);
        fileDO.setFileSize((long) bytes.length);
        fileDO.setStorageKey(storageKey);
        fileDO.setStorageType(storageClient instanceof MinioStorageClient ? "minio" : "local");
        fileDO.setFileUrl("/api/v1/files/" + fileUuid + "/download");
        fileDO.setMd5Hash(md5Hex(bytes));
        fileDO.setTenantId(tenantId);
        fileDO.setBizType(bizType);
        fileDO.setStatus(1);
        fileDO.setDeleted(0);
        fileMapper.insert(fileDO);

        log.info("文件上传成功: uuid={}, name={}, size={}, bizType={}", fileUuid, originalName, bytes.length, bizType);
        return fileDO;
    }

    /**
     * 下载文件
     */
    public InputStream download(String fileUuid) {
        FileDO fileDO = getByUuid(fileUuid);
        if (fileDO == null || fileDO.getStatus() != 1) {
            throw new RuntimeException("文件不存在或已删除: " + fileUuid);
        }
        return storageClient.download(fileDO.getStorageKey());
    }

    /**
     * 删除文件（软删除）
     */
    public void delete(String fileUuid) {
        FileDO fileDO = getByUuid(fileUuid);
        if (fileDO == null) {
            return;
        }
        storageClient.delete(fileDO.getStorageKey());
        fileDO.setStatus(0);
        fileMapper.updateById(fileDO);
        log.info("文件已删除: uuid={}", fileUuid);
    }

    /**
     * 按 UUID 查询文件元数据
     */
    public FileDO getByUuid(String fileUuid) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileDO> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(FileDO::getFileUuid, fileUuid);
        return fileMapper.selectOne(wrapper);
    }

    /**
     * 按 UUID 获取访问 URL
     */
    public String getUrl(String fileUuid) {
        FileDO fileDO = getByUuid(fileUuid);
        return fileDO != null ? fileDO.getFileUrl() : null;
    }

    private static String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    private static String md5Hex(byte[] bytes) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
