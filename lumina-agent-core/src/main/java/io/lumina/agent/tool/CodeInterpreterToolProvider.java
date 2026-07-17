package io.lumina.agent.tool;

import com.github.dockerjava.api.model.HostConfig;
import jakarta.annotation.PreDestroy;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

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
 *   <li>{@code docker}：在 Docker 容器中隔离执行（挂载脚本目录、内存/CPU 限制、
 *       可禁网络）。Docker 不可用时自动降级到 process 模式。</li>
 * </ul>
 *
 * <p>Docker 模式增强能力：
 * <ul>
 *   <li>容器池：{@code pool-size > 0} 时按镜像维度复用常驻容器，避免每次执行
 *       都冷启动容器；空闲超时（{@code pool-idle-timeout-minutes}）自动销毁。</li>
 *   <li>依赖安装：{@code auto-install-deps=true} 时，执行前自动安装依赖——
 *       支持代码内联声明（{@code # pip: pandas,numpy} / {@code // npm: lodash}）
 *       以及工作目录下的 requirements.txt / package.json。</li>
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

    /** 常驻容器的保活时长（秒），足够长以覆盖池空闲超时 */
    private static final long POOLED_CONTAINER_KEEPALIVE_SECONDS = 24L * 60 * 60;

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

    @Value("${lumina.agent.code-interpreter.auto-install-deps:false}")
    private boolean autoInstallDeps;

    @Value("${lumina.agent.code-interpreter.pool-size:2}")
    private int poolSize;

    @Value("${lumina.agent.code-interpreter.pool-idle-timeout-minutes:10}")
    private int poolIdleTimeoutMinutes;

    /**
     * 流式输出（预留，默认 false）
     *
     * <p>后续实现方向：真正的流式输出（SSE 推到前端）在 Agent 执行层面
     * 通过 StreamChunk 处理，工具本身仍一次性返回完整结果，
     * 因此当前不改变执行逻辑，仅预留配置开关。
     */
    @Value("${lumina.agent.code-interpreter.stream-output:false}")
    private boolean streamOutput;

    /** 容器池（懒初始化，poolSize=0 时不启用） */
    private volatile ContainerPool containerPool;

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
                List<String> inlineDeps = parseInlineDeps(lang, code);
                return executeInDocker(lang, scriptFile, inlineDeps, result);
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
        Process process = null;
        try {
            process = pb.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
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
        } finally {
            // 任何路径（超时、异常）都不残留子进程
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * Docker 容器隔离执行
     *
     * <p>启动一个保持存活的容器（sleep），把脚本目录挂载到 /code，
     * 再通过 execInContainer 执行脚本并自行控制超时——相比 withCommand 直接跑脚本，
     * 该方式能可靠拿到非零退出码（Testcontainers 的启动检查会把快速退出的
     * 非零容器判为启动失败）。资源限制：内存、CPU（NanoCpus）、可禁网络。
     *
     * <p>poolSize > 0 时从容器池借出常驻容器并在执行完归还（不 stop）；
     * poolSize = 0 时保持原有每次新建、用完销毁的逻辑。
     */
    private Map<String, Object> executeInDocker(String lang, Path scriptFile,
                                                List<String> inlineDeps, Map<String, Object> result) {
        String image = "python".equals(lang) ? pythonImage : nodeImage;
        String cmd = "python".equals(lang) ? "python3" : "node";
        String containerScriptPath = "/code/" + scriptFile.getFileName();
        Path codeDir = scriptFile.getParent();

        long startNanos = System.nanoTime();

        if (poolSize > 0) {
            // 容器池路径：借容器 → 执行 → 归还（容器保持运行以便复用）
            PooledContainer pooled = null;
            boolean containerReusable = true;
            try {
                pooled = pool().borrowContainer(image,
                        () -> createContainer(image, codeDir, POOLED_CONTAINER_KEEPALIVE_SECONDS));
                GenericContainer<?> container = pooled.getContainer();

                if (autoInstallDeps) {
                    installDepsInContainer(container, lang, codeDir, inlineDeps);
                }

                return execAndBuildResult(container, cmd, containerScriptPath, startNanos, result);
            } catch (TimeoutException te) {
                // 超时的容器内可能仍有进程在跑，不能归还池中复用
                containerReusable = false;
                return buildTimeoutResult(startNanos, result);
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
            } finally {
                if (pooled != null) {
                    if (containerReusable) {
                        pool().returnContainer(pooled);
                    } else {
                        log.warn("代码执行超时，销毁容器不归还池: image={}", image);
                        try {
                            pooled.getContainer().stop();
                        } catch (Exception se) {
                            log.warn("销毁超时容器失败: {}", se.getMessage());
                        }
                    }
                }
            }
        }

        // 原有一次性容器路径（poolSize=0，向后兼容）
        try (GenericContainer<?> container = createContainer(image, codeDir, timeoutSeconds + 60L)) {
            container.start();

            if (autoInstallDeps) {
                installDepsInContainer(container, lang, codeDir, inlineDeps);
            }

            return execAndBuildResult(container, cmd, containerScriptPath, startNanos, result);

        } catch (TimeoutException te) {
            return buildTimeoutResult(startNanos, result);
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
     * 创建容器（未启动），资源限制与网络配置和 docker 模式一致
     *
     * <p>autoInstallDeps=true 时强制放开网络（pip/npm 下载依赖需要联网，
     * 且 Docker 不支持运行时切换 networkDisabled），日志 warn 提示。
     */
    private GenericContainer<?> createContainer(String image, Path codeDir, long keepAliveSeconds) {
        boolean effectiveNetworkDisabled = networkDisabled;
        if (autoInstallDeps && networkDisabled) {
            log.warn("auto-install-deps=true 需要网络下载依赖，network-disabled 配置被忽略（容器将联网）");
            effectiveNetworkDisabled = false;
        }
        final boolean disableNetwork = effectiveNetworkDisabled;

        GenericContainer<?> container = new GenericContainer<>(image)
                .withFileSystemBind(codeDir.toString(), "/code", BindMode.READ_WRITE)
                .withCommand("sh", "-c", "sleep " + keepAliveSeconds)
                .withStartupTimeout(Duration.ofSeconds(30));

        container.withCreateContainerCmdModifier(createCmd -> {
            HostConfig hostConfig = createCmd.getHostConfig() != null
                    ? createCmd.getHostConfig()
                    : HostConfig.newHostConfig();
            hostConfig.withMemory((long) memoryLimitMb * 1024 * 1024);
            hostConfig.withNanoCPUs((long) (cpuLimit * 1_000_000_000L));
            createCmd.withHostConfig(hostConfig);
            if (disableNetwork) {
                createCmd.withNetworkDisabled(true);
            }
        });
        return container;
    }

    /**
     * 在容器内执行脚本（带超时）并组装返回结果
     *
     * <p>超时抛出 {@link TimeoutException} 由调用方处理——池化路径需要据此
     * 判定容器不可复用（超时的容器内可能仍有进程在跑）。
     */
    private Map<String, Object> execAndBuildResult(GenericContainer<?> container, String cmd,
                                                   String containerScriptPath, long startNanos,
                                                   Map<String, Object> result) throws Exception {
        Container.ExecResult execResult =
                execInContainerWithTimeout(container, timeoutSeconds, cmd, containerScriptPath);

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
    }

    /**
     * 组装超时结果
     */
    private Map<String, Object> buildTimeoutResult(long startNanos, Map<String, Object> result) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        result.put("success", false);
        result.put("error", "代码执行超时（超过 " + timeoutSeconds + " 秒）");
        result.put("durationMs", durationMs);
        return result;
    }

    /**
     * 在容器内执行命令，带超时控制
     *
     * <p>Testcontainers 的 execInContainer 不支持流式回调，真正的流式输出
     * 需要 Docker logs API 的 follow 模式，后续在 Agent 执行层面（StreamChunk）实现。
     */
    private Container.ExecResult execInContainerWithTimeout(
            GenericContainer<?> container, long timeoutSeconds, String... command) throws Exception {

        CompletableFuture<Container.ExecResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return container.execInContainer(command);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw te;
        }
    }

    /**
     * 在容器内安装依赖
     *
     * <p>两种来源，都存在时都会安装：
     * <ul>
     *   <li>代码内联声明：{@code # pip: pandas,numpy}（python）或
     *       {@code // npm: lodash,axios}（javascript）</li>
     *   <li>工作目录下的 requirements.txt（python）或 package.json（javascript）</li>
     * </ul>
     * 安装失败只记录 warn，不中断执行（脚本可能并不真正依赖这些包）。
     */
    private void installDepsInContainer(GenericContainer<?> container, String lang,
                                        Path codeDir, List<String> inlineDeps) {
        try {
            if ("python".equals(lang)) {
                if (!inlineDeps.isEmpty()) {
                    List<String> cmd = new ArrayList<>(List.of("pip", "install", "-q"));
                    cmd.addAll(inlineDeps);
                    log.info("容器内安装 Python 依赖: {}", inlineDeps);
                    container.execInContainer(cmd.toArray(new String[0]));
                }
                if (Files.exists(codeDir.resolve("requirements.txt"))) {
                    log.info("容器内安装 requirements.txt 依赖");
                    container.execInContainer("pip", "install", "-q", "-r", "/code/requirements.txt");
                }
            } else if ("javascript".equals(lang)) {
                if (!inlineDeps.isEmpty()) {
                    List<String> cmd = new ArrayList<>(List.of("npm", "install", "--prefix", "/code"));
                    cmd.addAll(inlineDeps);
                    log.info("容器内安装 Node 依赖: {}", inlineDeps);
                    container.execInContainer(cmd.toArray(new String[0]));
                }
                if (Files.exists(codeDir.resolve("package.json"))) {
                    log.info("容器内安装 package.json 依赖");
                    container.execInContainer("npm", "install", "--prefix", "/code");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("容器内依赖安装被中断: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("容器内依赖安装失败（继续执行脚本）: {}", e.getMessage());
        }
    }

    /**
     * 解析代码内联依赖声明
     *
     * <p>格式：{@code # pip: pandas,numpy,matplotlib}（python）
     * 或 {@code // npm: lodash,axios} / {@code # npm: lodash,axios}（javascript）。
     */
    private List<String> parseInlineDeps(String lang, String code) {
        List<String> deps = new ArrayList<>();
        String[] markers = "python".equals(lang)
                ? new String[]{"# pip:", "#pip:"}
                : new String[]{"// npm:", "//npm:", "# npm:", "#npm:"};

        for (String line : code.split("\n")) {
            String trimmed = line.trim();
            for (String marker : markers) {
                if (trimmed.toLowerCase(Locale.ROOT).startsWith(marker)) {
                    String spec = trimmed.substring(marker.length());
                    for (String dep : spec.split(",")) {
                        String d = dep.trim();
                        if (!d.isEmpty()) {
                            deps.add(d);
                        }
                    }
                    break;
                }
            }
        }
        return deps;
    }

    /**
     * 获取容器池（懒初始化，线程安全）
     */
    private ContainerPool pool() {
        ContainerPool p = containerPool;
        if (p == null) {
            synchronized (this) {
                if (containerPool == null) {
                    containerPool = new ContainerPool(poolSize, poolIdleTimeoutMinutes);
                }
                p = containerPool;
            }
        }
        return p;
    }

    /**
     * 应用关闭时销毁池内所有常驻容器
     */
    @PreDestroy
    public void shutdownPool() {
        ContainerPool p = containerPool;
        if (p != null) {
            p.shutdown();
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

    /**
     * 池化容器：包装 GenericContainer + 最后使用时间
     */
    private static class PooledContainer {
        private final String image;
        private final GenericContainer<?> container;
        private volatile long lastUsedTime;

        PooledContainer(String image, GenericContainer<?> container) {
            this.image = image;
            this.container = container;
            this.lastUsedTime = System.currentTimeMillis();
        }

        String getImage() {
            return image;
        }

        GenericContainer<?> getContainer() {
            return container;
        }

        long getLastUsedTime() {
            return lastUsedTime;
        }

        void touch() {
            this.lastUsedTime = System.currentTimeMillis();
        }
    }

    /**
     * 容器池：按 image 维度缓存常驻容器，空闲超时自动销毁
     *
     * <p>线程安全：ConcurrentHashMap + LinkedBlockingQueue。
     * 借出时若池中有存活容器则复用，否则新建；归还时更新最后使用时间，
     * 池满则直接销毁。空闲清理在每次借出时顺带触发。
     */
    private static class ContainerPool {
        private final int maxSize;
        private final long idleTimeoutMillis;
        /** key = image name, value = 该镜像的空闲容器队列 */
        private final Map<String, BlockingQueue<PooledContainer>> pools = new ConcurrentHashMap<>();

        ContainerPool(int maxSize, int idleTimeoutMinutes) {
            this.maxSize = maxSize;
            this.idleTimeoutMillis = TimeUnit.MINUTES.toMillis(idleTimeoutMinutes);
        }

        /**
         * 从池中借出容器：有空闲存活容器就复用，否则用 factory 新建并启动
         */
        PooledContainer borrowContainer(String image, Supplier<GenericContainer<?>> factory) {
            cleanupIdle();

            BlockingQueue<PooledContainer> queue =
                    pools.computeIfAbsent(image, k -> new LinkedBlockingQueue<>());

            PooledContainer pooled;
            while ((pooled = queue.poll()) != null) {
                if (pooled.getContainer().isRunning()) {
                    log.debug("容器池复用容器: image={}", image);
                    return pooled;
                }
                // 容器已死（如被外部清理），销毁并继续找下一个
                stopQuietly(pooled);
            }

            log.debug("容器池新建容器: image={}", image);
            GenericContainer<?> container = factory.get();
            container.start();
            return new PooledContainer(image, container);
        }

        /**
         * 归还容器到池（更新最后使用时间）；池已满或容器已停止则直接销毁
         */
        void returnContainer(PooledContainer pooled) {
            if (!pooled.getContainer().isRunning()) {
                stopQuietly(pooled);
                return;
            }
            pooled.touch();
            BlockingQueue<PooledContainer> queue =
                    pools.computeIfAbsent(pooled.getImage(), k -> new LinkedBlockingQueue<>());
            if (queue.size() >= maxSize || !queue.offer(pooled)) {
                log.debug("容器池已满，销毁归还容器: image={}", pooled.getImage());
                stopQuietly(pooled);
            }
        }

        /**
         * 销毁超时空闲容器（借出时顺带触发）
         */
        void cleanupIdle() {
            long now = System.currentTimeMillis();
            pools.values().forEach(queue ->
                    queue.removeIf(pooled -> {
                        if (now - pooled.getLastUsedTime() > idleTimeoutMillis) {
                            log.debug("容器池销毁空闲超时容器: image={}", pooled.getImage());
                            stopQuietly(pooled);
                            return true;
                        }
                        return false;
                    }));
        }

        /**
         * 销毁池内所有容器（应用关闭时调用）
         */
        void shutdown() {
            pools.values().forEach(queue -> {
                PooledContainer pooled;
                while ((pooled = queue.poll()) != null) {
                    stopQuietly(pooled);
                }
            });
            pools.clear();
        }

        private void stopQuietly(PooledContainer pooled) {
            try {
                pooled.getContainer().stop();
            } catch (Exception e) {
                log.warn("容器池销毁容器失败: {}", e.getMessage());
            }
        }
    }
}
