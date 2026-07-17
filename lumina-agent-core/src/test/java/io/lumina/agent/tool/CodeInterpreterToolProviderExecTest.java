package io.lumina.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 代码解释器实际执行测试
 *
 * <p>需要 python/node 在 PATH 中可用。通过环境变量 PYTHON_AVAILABLE 控制是否执行。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
class CodeInterpreterToolProviderExecTest {

    private CodeInterpreterToolProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CodeInterpreterToolProvider();
        ReflectionTestUtils.setField(provider, "timeoutSeconds", 10);
        ReflectionTestUtils.setField(provider, "maxOutputLength", 10000);
        // Windows 上 python 可能是 python 而非 python3
        ReflectionTestUtils.setField(provider, "pythonPath", "python");
        ReflectionTestUtils.setField(provider, "nodePath", "node");
        ReflectionTestUtils.setField(provider, "workDir",
                System.getProperty("java.io.tmpdir") + "/lumina-code-exec-test");
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
    void pythonExecutionReturnsOutput() {
        Map<String, Object> result = provider.execute("python",
                "print('Hello from Python!')\nprint(2 + 3)");

        System.out.println("=== Python 执行结果 ===");
        System.out.println("success: " + result.get("success"));
        System.out.println("stdout: " + result.get("stdout"));
        System.out.println("exitCode: " + result.get("exitCode"));
        System.out.println("durationMs: " + result.get("durationMs"));

        assertThat(result.get("success")).isEqualTo(true);
        assertThat((String) result.get("stdout")).contains("Hello from Python!");
        assertThat((String) result.get("stdout")).contains("5");
        assertThat(result.get("exitCode")).isEqualTo(0);
    }

    @Test
    void javascriptExecutionReturnsOutput() {
        Map<String, Object> result = provider.execute("javascript",
                "console.log('Hello from Node!'); console.log(10 * 5);");

        System.out.println("=== JavaScript 执行结果 ===");
        System.out.println("success: " + result.get("success"));
        System.out.println("stdout: " + result.get("stdout"));
        System.out.println("exitCode: " + result.get("exitCode"));
        System.out.println("durationMs: " + result.get("durationMs"));

        assertThat(result.get("success")).isEqualTo(true);
        assertThat((String) result.get("stdout")).contains("Hello from Node!");
        assertThat((String) result.get("stdout")).contains("50");
        assertThat(result.get("exitCode")).isEqualTo(0);
    }

    @Test
    void pythonErrorReturnsNonZeroExitCode() {
        Map<String, Object> result = provider.execute("python",
                "print(1/0)");

        System.out.println("=== Python 错误执行结果 ===");
        System.out.println("success: " + result.get("success"));
        System.out.println("stdout: " + result.get("stdout"));
        System.out.println("exitCode: " + result.get("exitCode"));

        // 除零错误应该返回非零退出码
        assertThat(result.get("success")).isEqualTo(false);
        assertThat((int) result.get("exitCode")).isNotEqualTo(0);
    }
}
