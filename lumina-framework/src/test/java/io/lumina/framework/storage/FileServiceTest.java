package io.lumina.framework.storage;

import io.lumina.common.core.BaseContext;
import io.lumina.framework.storage.entity.FileDO;
import io.lumina.framework.infrastructure.mapper.FileMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FileService 单元测试
 *
 * <p>覆盖文件上传（MD5/路径生成/存储类型判定）、下载（状态校验）、删除（软删除）、查询。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @InjectMocks
    private FileService fileService;

    @Mock
    private StorageClient storageClient;

    @Mock
    private FileMapper fileMapper;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void uploadBytesReturnsFileDO() {
        byte[] content = "test content".getBytes();
        when(storageClient.upload(any(byte[].class), anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(fileMapper.insert(any(FileDO.class))).thenAnswer(inv -> {
            ((FileDO) inv.getArgument(0)).setFileId(1L);
            return 1;
        });

        FileDO result = fileService.upload(content, "photo.png", "image/png", "chat_image");

        assertThat(result).isNotNull();
        assertThat(result.getFileUuid()).hasSize(32);
        assertThat(result.getOriginalName()).isEqualTo("photo.png");
        assertThat(result.getContentType()).isEqualTo("image/png");
        assertThat(result.getFileSize()).isEqualTo(content.length);
        assertThat(result.getFileUrl()).startsWith("/api/v1/files/");
        assertThat(result.getFileUrl()).endsWith("/download");
        assertThat(result.getMd5Hash()).hasSize(32);
        assertThat(result.getTenantId()).isEqualTo(1L);
        assertThat(result.getBizType()).isEqualTo("chat_image");
        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getStorageType()).isEqualTo("local");
        assertThat(result.getStorageKey()).startsWith("1/");
        assertThat(result.getStorageKey()).contains(".png");

        verify(storageClient).upload(eq(content), anyString(), eq("image/png"));
        verify(fileMapper).insert(any(FileDO.class));
    }

    @Test
    void uploadNoExtension() {
        byte[] content = "noext".getBytes();
        when(storageClient.upload(any(byte[].class), anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(fileMapper.insert(any(FileDO.class))).thenReturn(1);

        FileDO result = fileService.upload(content, "Makefile", "text/plain", "doc");

        assertThat(result.getStorageKey()).doesNotContain(".");
    }

    @Test
    void uploadNullTenantDefaultsToZero() {
        BaseContext.clear();

        byte[] content = "data".getBytes();
        when(storageClient.upload(any(byte[].class), anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(fileMapper.insert(any(FileDO.class))).thenReturn(1);

        FileDO result = fileService.upload(content, "f.txt", "text/plain", "doc");

        assertThat(result.getTenantId()).isEqualTo(0L);
        assertThat(result.getStorageKey()).startsWith("0/");
    }

    @Test
    void downloadReturnsStream() {
        FileDO fileDO = new FileDO();
        fileDO.setFileUuid("abc");
        fileDO.setStorageKey("1/2026/01/abc.txt");
        fileDO.setStatus(1);

        when(fileMapper.selectOne(any())).thenReturn(fileDO);
        InputStream mockStream = mock(InputStream.class);
        when(storageClient.download("1/2026/01/abc.txt")).thenReturn(mockStream);

        InputStream result = fileService.download("abc");

        assertThat(result).isNotNull();
        verify(storageClient).download("1/2026/01/abc.txt");
    }

    @Test
    void downloadNonExistentThrows() {
        when(fileMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> fileService.download("notfound"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文件不存在");
    }

    @Test
    void downloadDeletedFileThrows() {
        FileDO fileDO = new FileDO();
        fileDO.setStatus(0);
        when(fileMapper.selectOne(any())).thenReturn(fileDO);

        assertThatThrownBy(() -> fileService.download("deleted"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void deleteSoftDeletesAndRemovesFromStorage() {
        FileDO fileDO = new FileDO();
        fileDO.setFileUuid("del");
        fileDO.setStorageKey("1/2026/01/del.txt");
        fileDO.setStatus(1);

        when(fileMapper.selectOne(any())).thenReturn(fileDO);

        fileService.delete("del");

        verify(storageClient).delete("1/2026/01/del.txt");
        assertThat(fileDO.getStatus()).isEqualTo(0);
        verify(fileMapper).updateById(any(FileDO.class));
    }

    @Test
    void deleteNonExistentIsNoop() {
        when(fileMapper.selectOne(any())).thenReturn(null);

        fileService.delete("ghost");

        verify(storageClient, never()).delete(anyString());
        verify(fileMapper, never()).updateById(any(FileDO.class));
    }

    @Test
    void getByUuidQueriesMapper() {
        FileDO fileDO = new FileDO();
        fileDO.setFileUuid("findme");
        when(fileMapper.selectOne(any())).thenReturn(fileDO);

        FileDO result = fileService.getByUuid("findme");

        assertThat(result).isNotNull();
        assertThat(result.getFileUuid()).isEqualTo("findme");
    }

    @Test
    void getUrlReturnsFileUrl() {
        FileDO fileDO = new FileDO();
        fileDO.setFileUrl("/api/v1/files/xyz/download");
        when(fileMapper.selectOne(any())).thenReturn(fileDO);

        String url = fileService.getUrl("xyz");
        assertThat(url).isEqualTo("/api/v1/files/xyz/download");
    }

    @Test
    void getUrlReturnsNullIfNotFound() {
        when(fileMapper.selectOne(any())).thenReturn(null);

        String url = fileService.getUrl("ghost");
        assertThat(url).isNull();
    }

    @Test
    void md5HashDeterministic() {
        byte[] content = "consistent".getBytes();
        when(storageClient.upload(any(byte[].class), anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(fileMapper.insert(any(FileDO.class))).thenReturn(1);

        FileDO r1 = fileService.upload(content, "a.txt", "text/plain", "doc");
        FileDO r2 = fileService.upload(content, "b.txt", "text/plain", "doc");

        assertThat(r1.getMd5Hash()).isEqualTo(r2.getMd5Hash());
    }

    @Test
    void minioStorageTypeDetected() {
        StorageClient minioClient = mock(MinioStorageClient.class);
        ReflectionTestUtils.setField(fileService, "storageClient", minioClient);

        byte[] content = "m".getBytes();
        when(minioClient.upload(any(byte[].class), anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(fileMapper.insert(any(FileDO.class))).thenReturn(1);

        FileDO result = fileService.upload(content, "f.txt", "text/plain", "doc");

        assertThat(result.getStorageType()).isEqualTo("minio");
    }

    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}
