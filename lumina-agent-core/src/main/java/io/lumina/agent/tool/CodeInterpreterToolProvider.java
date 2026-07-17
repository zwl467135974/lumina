package io.lumina.agent.tool;

import com.github.dockerjava.api.model.HostConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 代码解释器工具提供者
 *
 * <p>为 Agent 提供代码执行能力，支持 Python 和 JavaScript。
 * 通过 {@code lumina.agent.code-interpreter.enabled=true} 启用。
 *
 * <p>执行方式由 {@code lumina.agent.code-interpreter.mode} 控制：
 * <ul>
 *   <li>{@code process}（默认）：将代码写入临时文件，通过 {@link ProcessBuilder}
 *       调用外部解释器（python/node），带超时控制与输出截断。</li>
 *   <li>{@code docker}：在 Docker 容器中隔离执行（只读挂载脚本目录、内存/CPU 限制、
 *       可禁网络）。Docker 不可用时自动降级到 process 模式。</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.agent.code-interpreter", name = "enabled", havingValue = "true")
public class CodeInterpreterToolProvider {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("python", "javascript");

    @Value("${lumina.agent.code-interpreter.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${lumina.agent.code-interpreter.max-output-length:10000}")
    private int maxOutputLength;

    @Value("${lumina.agent.code-interpreter.python-path:python3}")
    private String pythonPath;

    @Value("${lumina.agent.code-interpreter.node-path:node}")
    private String nodePath;

    @Value("#{T(java.lang.System).getProperty('java.io.tmpdir')+'/lumina-code'}")
    private String workDir;

    @Value("${lumina.agent.code-interpreter.mode:process}")
    private String mode;

    @Value("${lumina.agent.code-interpreter.python-image:python:3.11-slim}")
    private String pythonImage;

    @Value("${lumina.agent.code-interpreter.node-image:node:20-slim}")
    private String nodeImage;

    @Value("${lumina.agent.code-interpreter.memory-limit-mb:256}")
    private int memoryLimitMb;

    @Value("${lumina.agent.code-interpreter.cpu-limit:1.0}")
    private double cpuLimit;

    @Value("${lumina.agent.code-interpreter.network-disabled:true}")
    private boolean networkDisabled;

    /**
     * 执行代码
     *
     * <p>校验语言白名单与代码非空后，将代码写入临时文件并执行，
     * 返回执行结果（成功标志、标准输出、退出码、耗时）。
     * mode=docker 且 Docker 可用时在容器内隔离执行，否则走本地进程。
     */
    @AgentTool(
        name = "code.execute",
        description = "执行代码并返回输出结果。支持 python 和 javascript 语言。用于数据分析、计算等。",
        category = "code.interpreter"
    )
    public Map<String, Object> execute(String language, String code) {
        log.info("Agent 调用代码解释器: language={}, mode={}", language, mode);

        Map<String, Object> result = new HashMap<>();

        // 语言白名单校验
        String lang = language != null ? language.trim().toLowerCase() : "";
        if (!SUPPORTED_LANGUAGES.contains(lang)) {
            result.put("success", false);
            result.put("error", "不支持的语言: " + language + "，仅支持 python 和 javascript");
            return result;
        }

        // 空代码校验
        if (code == null || code.isBlank()) {
            result.put("success", false);
            result.put("error", "代码不能为空");
            return result;
        }

        Path scriptFile = null;
        try {
            Path dir = Path.of(workDir);
            Files.createDirectories(dir);

            String suffix = "python".equals(lang) ? ".py" : ".js";
            scriptFile = Files.createTempFile(dir, "code-", suffix);
            Files.writeString(scriptFile, code, StandardCharsets.UTF_8);

            if ("docker".equalsIgnoreCase(mode) && isDockerAvailable()) {
                return executeInDocker(lang, scriptFile, result);
            }
            return executeInProcess(lang, scriptFile, result);

        } catch (Exception e) {
            log.error("代码执行失败: language={}, error={}", language, e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        } finally {
            if (scriptFile != null) {
                try {
                    Files.deleteIfExists(scriptFile);
                } catch (IOException e) {
                    log.warn("删除临时脚本文件失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 检查 Docker 是否可用（不可用时降级到本地进程执行）
     */
    private boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            log.warn("Docker 不可用，降级到 process 模式: {}", t.getMessage());
            return false;
        }
    }

    /**
     * 本地进程执行（原有 ProcessBuilder 逻辑）
     */
    private Map<String, Object> executeInProcess(String lang, Path scriptFile, Map<String, Object> result)
            throws IOException, InterruptedException {
        String interpreter = "python".equals(lang) ? pythonPath : nodePath;

        ProcessBuilder pb = new ProcessBuilder(interpreter, scriptFile.toString());
        pb.redirectErrorStream(true);
        pb.directory(scriptFile.getParent().toFile());

        long startNanos = System.nanoTime();
        Process process = pb.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            result.put("success", false);
            result.put("error", "代码执行超时（超过 " + timeoutSeconds + " 秒）");
            result.put("durationMs", durationMs);
            return result;
        }

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        stdout = truncate(stdout);

        int exitCode = process.exitValue();
        result.put("success", exitCode == 0);
        result.put("stdout", stdout);
        result.put("exitCode", exitCode);
        result.put("durationMs", durationMs);
        return result;
    }

    /**
     * Docker 容器隔离执行
     *
     * <p>启动一个保持存活的容器（sleep），把脚本目录挂载到 /code，
     * 再通过 execInContainer 执行脚本并自行控制超时——相比 withCommand 直接跑脚本，
     * 该方式能可靠拿到非零退出码（Testcontainers 的启动检查会把快速退出的
     * 非零容器判为启动失败）。资源限制：内存、CPU（NanoCpus）、可禁网络。
     */
    private Map<String, Object> executeInDocker(String lang, Path scriptFile, Map<String, Object> result) {
        String image = "python".equals(lang) ? pythonImage : nodeImage;
        String cmd = "python".equals(lang) ? "python3" : "node";
        String containerScriptPath = "/code/" + scriptFile.getFileName();

        long startNanos = System.nanoTime();
        try (GenericContainer<?> container = new GenericContainer<>(image)
                .withFileSystemBind(scriptFile.getParent().toString(), "/code", BindMode.READ_WRITE)
                .withCommand("sh", "-c", "sleep " + (timeoutSeconds + 60L))
                .withStartupTimeout(Duration.ofSeconds(30))) {

            container.withCreateContainerCmdModifier(createCmd -> {
                HostConfig hostConfig = createCmd.getHostConfig() != null
                        ? createCmd.getHostConfig()
                        : HostConfig.newHostConfig();
                hostConfig.withMemory((long) memoryLimitMb * 1024 * 1024);
                hostConfig.withNanoCPUs((long) (cpuLimit * 1_000_000_000L));
                createCmd.withHostConfig(hostConfig);
                if (networkDisabled) {
                    createCmd.withNetworkDisabled(true);
                }
            });

            container.start();

            CompletableFuture<Container.ExecResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return container.execInContainer(cmd, containerScriptPath);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });

            Container.ExecResult execResult;
            try {
                execResult = future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                future.cancel(true);
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                result.put("success", false);
                result.put("error", "代码执行超时（超过 " + timeoutSeconds + " 秒）");
                result.put("durationMs", durationMs);
                return result;
            }

            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

            String stdout = execResult.getStdout();
            String stderr = execResult.getStderr();
            String output = (stderr == null || stderr.isEmpty()) ? stdout : stdout + stderr;
            output = truncate(output);

            int exitCode = execResult.getExitCode();
            result.put("success", exitCode == 0);
            result.put("stdout", output);
            result.put("exitCode", exitCode);
            result.put("durationMs", durationMs);
            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.put("success", false);
            result.put("error", "Docker 执行被中断: " + e.getMessage());
            return result;
        } catch (Exception e) {
            log.error("Docker 执行失败: image={}, error={}", image, e.getMessage());
            result.put("success", false);
            result.put("error", "Docker 执行失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 输出截断
     */
    private String truncate(String output) {
        if (output != null && output.length() > maxOutputLength) {
            return output.substring(0, maxOutputLength)
                    + "\n... (已截断，完整输出 " + output.length() + " 字符)";
        }
        return output;
    }
}
