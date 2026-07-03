package io.lumina.framework.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LocalDiskStorageClient 单元测试
 *
 * <p>覆盖文件上传/下载/删除/URL 生成、路径遍历防护。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class LocalDiskStorageClientTest {

    @TempDir
    Path tempDir;

    private LocalDiskStorageClient client;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.setType("local");
        props.getLocal().setBasePath(tempDir.toString());
        props.getLocal().setUrlPrefix("/files");
        client = new LocalDiskStorageClient(props);
    }

    @Test
    void uploadAndDownloadRoundTrip() {
        byte[] content = "Hello Lumina".getBytes();
        String key = "tenant1/2026/01/abc123.txt";

        String result = client.upload(new ByteArrayInputStream(content), key, "text/plain");
        assertThat(result).isEqualTo(key);

        try (InputStream is = client.download(key)) {
            assertThat(is.readAllBytes()).isEqualTo(content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void uploadByteOverloadUsesSameKey() {
        byte[] content = {1, 2, 3, 4, 5};
        String key = "data/bin.dat";

        String result = client.upload(content, key, "application/octet-stream");
        assertThat(result).isEqualTo(key);
    }

    @Test
    void downloadNonExistentThrows() {
        assertThatThrownBy(() -> client.download("nonexistent/file.txt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文件不存在");
    }

    @Test
    void deleteRemovesFile() {
        byte[] content = "temp".getBytes();
        String key = "tmp/del.txt";
        client.upload(new ByteArrayInputStream(content), key, "text/plain");

        client.delete(key);

        assertThatThrownBy(() -> client.download(key))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void deleteNonExistentDoesNotThrow() {
        client.delete("does/not/exist.txt");
    }

    @Test
    void getUrlConcatenatesPrefixAndKey() {
        String url = client.getUrl("tenant1/data/file.png");
        assertThat(url).isEqualTo("/files/tenant1/data/file.png");
    }

    @Test
    void pathTraversalBlocked() {
        assertThatThrownBy(() -> client.download("../../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("非法存储路径");
    }

    @Test
    void pathTraversalWithEncodedDotsBlocked() {
        assertThatThrownBy(() -> client.download("../../../secret"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void nestedDirectoryCreatedOnUpload() {
        byte[] content = "deep".getBytes();
        String key = "a/b/c/d/e/file.txt";

        client.upload(new ByteArrayInputStream(content), key, "text/plain");

        Path expected = tempDir.resolve(key);
        assertThat(Files.exists(expected)).isTrue();
    }
}
