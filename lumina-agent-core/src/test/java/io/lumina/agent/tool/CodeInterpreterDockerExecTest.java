package io.lumina.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Code Interpreter Docker 模式真实执行测试
 *
 * <p>需要 Docker 环境。在容器内执行 Python/JavaScript 代码，验证：
 * - 容器隔离执行正确
 * - stdout 正确捕获
 * - 非零退出码正确返回
 * - 网络隔离生效（禁止访问外网）
 *
 * @author Lumina Team
 * @since 3.3.1
 */
class CodeInterpreterDockerExecTest {

    private CodeInterpreterToolProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CodeInterpreterToolProvider();
        ReflectionTestUtils.setField(provider, "timeoutSeconds", 60);
        ReflectionTestUtils.setField(provider, "maxOutputLength", 10000);
        ReflectionTestUtils.setField(provider, "pythonPath", "python");
        ReflectionTestUtils.setField(provider, "nodePath", "node");
        ReflectionTestUtils.setField(provider, "workDir",
                System.getProperty("java.io.tmpdir") + "/lumina-code-docker-test");
        // Docker 模式配置
        ReflectionTestUtils.setField(provider, "mode", "docker");
        ReflectionTestUtils.setField(provider, "pythonImage", "python:3.11-slim");
        ReflectionTestUtils.setField(provider, "nodeImage", "node:20-slim");
        ReflectionTestUtils.setField(provider, "memoryLimitMb", 256);
        ReflectionTestUtils.setField(provider, "cpuLimit", 1.0);
        ReflectionTestUtils.setField(provider, "networkDisabled", true);
    }

    @Test
    void dockerPythonExecution() {
        Map<String, Object> result = provider.execute("python",
                "print('Hello from Docker Python!')\nimport sys\nprint(f'Python {sys.version}')\nprint(2 ** 10)");

        System.out.println("=== Docker Python 执行结果 ===");
        System.out.println("success: " + result.get("success"));
        System.out.println("stdout: " + result.get("stdout"));
        System.out.println("exitCode: " + result.get("exitCode"));
        System.out.println("durationMs: " + result.get("durationMs"));

        assertThat(result.get("success")).isEqualTo(true);
        assertThat((String) result.get("stdout")).contains("Hello from Docker Python!");
        assertThat((String) result.get("stdout")).contains("1024");
        assertThat(result.get("exitCode")).isEqualTo(0);
    }

    @Test
    void dockerNodeExecution() {
        Map<String, Object> result = provider.execute("javascript",
                "console.log('Hello from Docker Node!'); console.log(process.version); console.log(10 * 5);");

        System.out.println("=== Docker Node 执行结果 ===");
        System.out.println("success: " + result.get("success"));
        System.out.println("stdout: " + result.get("stdout"));
        System.out.println("exitCode: " + result.get("exitCode"));
        System.out.println("durationMs: " + result.get("durationMs"));

        assertThat(result.get("success")).isEqualTo(true);
        assertThat((String) result.get("stdout")).contains("Hello from Docker Node!");
        assertThat((String) result.get("stdout")).contains("50");
        assertThat(result.get("exitCode")).isEqualTo(0);
    }

    @Test
    void dockerErrorReturnsNonZeroExitCode() {
        Map<String, Object> result = provider.execute("python",
                "x = 1 / 0");

        System.out.println("=== Docker Python 错误执行结果 ===");
        System.out.println("success: " + result.get("success"));
        System.out.println("stdout: " + result.get("stdout"));
        System.out.println("exitCode: " + result.get("exitCode"));

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((int) result.get("exitCode")).isNotEqualTo(0);
        assertThat((String) result.get("stdout")).contains("ZeroDivisionError");
    }

    @Test
    void dockerNetworkIsolation() {
        // networkDisabled=true，尝试访问外网应该失败
        Map<String, Object> result = provider.execute("python",
                "import urllib.request\ntry:\n    urllib.request.urlopen('http://8.8.8.8', timeout=3)\n    print('NETWORK_ALLOWED')\nexcept Exception as e:\n    print(f'NETWORK_BLOCKED: {type(e).__name__}')");

        System.out.println("=== Docker 网络隔离测试 ===");
        System.out.println("success: " + result.get("success"));
        System.out.println("stdout: " + result.get("stdout"));

        // 网络被禁，应该输出 NETWORK_BLOCKED
        assertThat((String) result.get("stdout")).contains("NETWORK_BLOCKED");
    }
}
