package io.lumina.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 代码解释器工具提供者
 *
 * <p>为 Agent 提供代码执行能力，支持 Python 和 JavaScript。
 * 通过 {@code lumina.agent.code-interpreter.enabled=true} 启用。
 *
 * <p>执行方式：将代码写入临时文件，通过 {@link ProcessBuilder} 调用外部解释器
 * （python/node），带超时控制与输出截断，执行后清理临时文件。
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

    /**
     * 执行代码
     *
     * <p>校验语言白名单与代码非空后，将代码写入临时文件并调用对应解释器执行，
     * 返回执行结果（成功标志、标准输出、退出码、耗时）。
     */
    @AgentTool(
        name = "code.execute",
        description = "执行代码并返回输出结果。支持 python 和 javascript 语言。用于数据分析、计算等。",
        category = "code.interpreter"
    )
    public Map<String, Object> execute(String language, String code) {
        log.info("Agent 调用代码解释器: language={}", language);

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

            String interpreter = "python".equals(lang) ? pythonPath : nodePath;

            ProcessBuilder pb = new ProcessBuilder(interpreter, scriptFile.toString());
            pb.redirectErrorStream(true);
            pb.directory(dir.toFile());

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
            if (stdout.length() > maxOutputLength) {
                stdout = stdout.substring(0, maxOutputLength)
                        + "\n... (已截断，完整输出 " + stdout.length() + " 字符)";
            }

            int exitCode = process.exitValue();
            result.put("success", exitCode == 0);
            result.put("stdout", stdout);
            result.put("exitCode", exitCode);
            result.put("durationMs", durationMs);
            return result;

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
}
