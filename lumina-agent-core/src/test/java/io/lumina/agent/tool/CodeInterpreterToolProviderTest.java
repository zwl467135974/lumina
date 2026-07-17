package io.lumina.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 代码解释器工具提供者单元测试
 *
 * <p>覆盖入参校验分支：不支持的语言 / null 代码 / 空代码，均应返回 success=false。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@ExtendWith(MockitoExtension.class)
class CodeInterpreterToolProviderTest {

    private CodeInterpreterToolProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CodeInterpreterToolProvider();
        ReflectionTestUtils.setField(provider, "timeoutSeconds", 30);
        ReflectionTestUtils.setField(provider, "maxOutputLength", 10000);
        ReflectionTestUtils.setField(provider, "pythonPath", "python3");
        ReflectionTestUtils.setField(provider, "nodePath", "node");
        ReflectionTestUtils.setField(provider, "workDir",
                System.getProperty("java.io.tmpdir") + "/lumina-code-test");
        // Docker 相关字段（测试用 process 模式，不依赖 Docker）
        ReflectionTestUtils.setField(provider, "mode", "process");
        ReflectionTestUtils.setField(provider, "pythonImage", "python:3.11-slim");
        ReflectionTestUtils.setField(provider, "nodeImage", "node:20-slim");
        ReflectionTestUtils.setField(provider, "memoryLimitMb", 256);
        ReflectionTestUtils.setField(provider, "cpuLimit", 1.0);
        ReflectionTestUtils.setField(provider, "networkDisabled", true);
        // 增强功能字段（测试时全部关闭，保持原有行为）
        ReflectionTestUtils.setField(provider, "autoInstallDeps", false);
        ReflectionTestUtils.setField(provider, "poolSize", 0);
        ReflectionTestUtils.setField(provider, "poolIdleTimeoutMinutes", 10);
        ReflectionTestUtils.setField(provider, "streamOutput", false);
    }

    @Test
    void execute_unsupportedLanguage_returnsFailure() {
        Map<String, Object> result = provider.execute("ruby", "puts 1");

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error")).asString().contains("不支持的语言");
    }

    @Test
    void execute_nullCode_returnsFailure() {
        Map<String, Object> result = provider.execute("python", null);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error")).asString().contains("代码不能为空");
    }

    @Test
    void execute_blankCode_returnsFailure() {
        Map<String, Object> result = provider.execute("javascript", "   ");

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error")).asString().contains("代码不能为空");
    }
}
