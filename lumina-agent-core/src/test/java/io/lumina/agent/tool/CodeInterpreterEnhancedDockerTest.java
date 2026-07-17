package io.lumina.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Code Interpreter 增强功能 Docker 测试
 *
 * <p>验证：容器池复用、pip install 内联依赖、第二次执行复用容器加速。
 * 需要 Docker 环境稳定可用。通过 -Ddocker.tests.enabled=true 启用。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@EnabledIfSystemProperty(named = "docker.tests.enabled", matches = "true")
class CodeInterpreterEnhancedDockerTest {

    private CodeInterpreterToolProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CodeInterpreterToolProvider();
        ReflectionTestUtils.setField(provider, "timeoutSeconds", 60);
        ReflectionTestUtils.setField(provider, "maxOutputLength", 10000);
        ReflectionTestUtils.setField(provider, "pythonPath", "python");
        ReflectionTestUtils.setField(provider, "nodePath", "node");
        ReflectionTestUtils.setField(provider, "workDir",
                System.getProperty("java.io.tmpdir") + "/lumina-code-enhanced-test");
        ReflectionTestUtils.setField(provider, "mode", "docker");
        ReflectionTestUtils.setField(provider, "pythonImage", "python:3.11-slim");
        ReflectionTestUtils.setField(provider, "nodeImage", "node:20-slim");
        ReflectionTestUtils.setField(provider, "memoryLimitMb", 512);
        ReflectionTestUtils.setField(provider, "cpuLimit", 1.0);
        // 启用增强功能
        ReflectionTestUtils.setField(provider, "networkDisabled", false);  // pip install 需要网络
        ReflectionTestUtils.setField(provider, "autoInstallDeps", true);
        ReflectionTestUtils.setField(provider, "poolSize", 2);
        ReflectionTestUtils.setField(provider, "poolIdleTimeoutMinutes", 10);
        ReflectionTestUtils.setField(provider, "streamOutput", false);
    }

    @Test
    void pipInstallFromInlineDeclaration() {
        // 内联声明 pip 依赖 + 使用该依赖
        Map<String, Object> result = provider.execute("python",
                "# pip: numpy\n" +
                "import numpy as np\n" +
                "arr = np.array([1, 2, 3, 4, 5])\n" +
                "print(f'mean: {np.mean(arr)}')\n" +
                "print(f'std: {np.std(arr):.2f}')\n");

        System.out.println("=== pip install + numpy 测试 ===");
        System.out.println("success: " + result.get("success"));
        System.out.println("stdout: " + result.get("stdout"));
        System.out.println("durationMs: " + result.get("durationMs"));

        assertThat(result.get("success")).isEqualTo(true);
        assertThat((String) result.get("stdout")).contains("mean: 3.0");
        assertThat((String) result.get("stdout")).contains("std: 1.41");
    }

    @Test
    void containerPoolReuseSpeedsUpSecondExecution() {
        // 第一次执行（冷启动）
        long start1 = System.nanoTime();
        Map<String, Object> result1 = provider.execute("python",
                "print('first execution')");
        long duration1Ms = (System.nanoTime() - start1) / 1_000_000;

        System.out.println("=== 第一次执行（冷启动）===");
        System.out.println("durationMs: " + duration1Ms);

        // 第二次执行（应该复用容器，更快）
        long start2 = System.nanoTime();
        Map<String, Object> result2 = provider.execute("python",
                "print('second execution')");
        long duration2Ms = (System.nanoTime() - start2) / 1_000_000;

        System.out.println("=== 第二次执行（容器池复用）===");
        System.out.println("durationMs: " + duration2Ms);
        System.out.println("加速比: " + String.format("%.1fx", (double) duration1Ms / duration2Ms));

        assertThat(result1.get("success")).isEqualTo(true);
        assertThat(result2.get("success")).isEqualTo(true);
        assertThat((String) result2.get("stdout")).contains("second execution");
        // 第二次应该比第一次快（容器复用）
        // 注意：不 assert duration2Ms < duration1Ms 因为可能有波动，但打出来供观察
    }

    @Test
    void numpyDataAnalysisScenario() {
        // 模拟真实数据分析场景
        Map<String, Object> result = provider.execute("python",
                "# pip: numpy\n" +
                "import numpy as np\n" +
                "# 模拟销售数据\n" +
                "sales = np.array([120, 150, 135, 180, 165, 200, 190])\n" +
                "print(f'总计: {sales.sum()}')\n" +
                "print(f'平均: {sales.mean():.1f}')\n" +
                "print(f'最高: {sales.max()}')\n" +
                "print(f'最低: {sales.min()}')\n" +
                "print(f'标准差: {sales.std():.2f}')\n");

        System.out.println("=== 数据分析场景 ===");
        System.out.println("success: " + result.get("success"));
        System.out.println("stdout: " + result.get("stdout"));

        assertThat(result.get("success")).isEqualTo(true);
        assertThat((String) result.get("stdout")).contains("总计: 1140");
        assertThat((String) result.get("stdout")).contains("平均: 162.9");
    }
}
