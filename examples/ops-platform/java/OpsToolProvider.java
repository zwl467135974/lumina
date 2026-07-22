package io.lumina.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 运维工具提供者 — 智能运维平台最佳实践
 *
 * <p>为 Agent 提供系统运维数据读取能力，包括：
 * <ul>
 *   <li>{@code ops.readLogs} — 读取 Nginx / 应用日志</li>
 *   <li>{@code ops.readMetrics} — 读取 CPU / 内存 / 磁盘指标</li>
 *   <li>{@code ops.executeCommand} — 执行只读诊断命令（受限白名单）</li>
 * </ul>
 *
 * <p>数据由 {@code examples/ops-platform/scripts/gen_mock_data.py} 生成，
 * 默认读取 {@code /tmp/lumina-ops/} 目录，可通过
 * {@code lumina.ops.data-dir} 配置修改。
 *
 * <p>仅在 {@code lumina.ops.enabled=true} 时激活（默认关闭），
 * 避免影响非运维场景的 Agent。
 *
 * @author Lumina Team
 * @since 3.6.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.ops", name = "enabled", havingValue = "true")
public class OpsToolProvider {

    /**
     * 模拟数据根目录（由 gen_mock_data.py 生成）
     */
    @Value("${lumina.ops.data-dir:/tmp/lumina-ops}")
    private String dataDir;

    /**
     * 允许执行的只读诊断命令白名单（安全限制：只允许信息查看类命令）
     */
    private static final List<String> ALLOWED_COMMANDS = List.of(
            "ps", "df", "free", "uptime", "date", "hostname",
            "netstat", "ss", "ls", "cat", "head", "tail", "wc"
    );

    /**
     * 读取系统日志
     *
     * <p>Agent 通过此工具读取 Nginx access log 或应用错误日志，
     * 用于分析系统异常。
     *
     * @param type  日志类型：nginx（访问日志）/ app（应用日志）
     * @param lines 读取最后 N 行（默认 100，最大 500）
     * @return 日志内容
     */
    @AgentTool(
            name = "ops.readLogs",
            description = "读取系统日志。type=nginx 读取 Nginx 访问日志，type=app 读取应用错误日志。lines=读取最后N行（默认100）。",
            category = "ops.monitoring"
    )
    public Map<String, Object> readLogs(String type, int lines) {
        log.info("Agent 调用运维工具 readLogs: type={}, lines={}", type, lines);

        Map<String, Object> result = new HashMap<>();
        try {
            if (lines <= 0 || lines > 500) {
                lines = 100;
            }

            String subdir = "nginx".equalsIgnoreCase(type) ? "nginx" : "app";
            String filename = "nginx".equalsIgnoreCase(type) ? "access.log" : "error.log";
            Path logPath = Paths.get(dataDir, subdir, filename);

            if (!Files.exists(logPath)) {
                result.put("success", false);
                result.put("error", "日志文件不存在: " + logPath + "。请先运行 gen_mock_data.py 生成模拟数据。");
                return result;
            }

            List<String> allLines;
            try (Stream<String> stream = Files.lines(logPath, StandardCharsets.UTF_8)) {
                allLines = stream.collect(Collectors.toList());
            }

            int fromIndex = Math.max(0, allLines.size() - lines);
            List<String> tail = allLines.subList(fromIndex, allLines.size());

            // 统计 HTTP 状态码分布（仅 Nginx 日志）
            Map<String, Integer> stats = new HashMap<>();
            if ("nginx".equalsIgnoreCase(type)) {
                for (String line : tail) {
                    String status = extractHttpStatus(line);
                    if (status != null) {
                        String category = status.startsWith("5") ? "5xx"
                                : status.startsWith("4") ? "4xx"
                                : status.startsWith("3") ? "3xx" : "2xx";
                        stats.merge(category, 1, Integer::sum);
                    }
                }
            }

            result.put("success", true);
            result.put("type", type);
            result.put("totalLines", allLines.size());
            result.put("returnedLines", tail.size());
            result.put("content", String.join("\n", tail));
            if (!stats.isEmpty()) {
                result.put("statusDistribution", stats);
            }

        } catch (IOException e) {
            log.error("读取日志失败: type={}, error={}", type, e.getMessage());
            result.put("success", false);
            result.put("error", "读取日志失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 读取系统指标
     *
     * <p>Agent 通过此工具获取当前 CPU、内存、磁盘使用率及历史趋势。
     *
     * @param type 指标类型：cpu / memory / disk / all（默认 all）
     * @return 指标数据 JSON
     */
    @AgentTool(
            name = "ops.readMetrics",
            description = "读取系统指标。type=cpu/memory/disk/all。返回当前值和历史趋势（最近10个采样点）。",
            category = "ops.monitoring"
    )
    public Map<String, Object> readMetrics(String type) {
        log.info("Agent 调用运维工具 readMetrics: type={}", type);

        Map<String, Object> result = new HashMap<>();
        try {
            Path metricsPath = Paths.get(dataDir, "metrics", "cpu.json");

            if (!Files.exists(metricsPath)) {
                result.put("success", false);
                result.put("error", "指标文件不存在: " + metricsPath + "。请先运行 gen_mock_data.py 生成模拟数据。");
                return result;
            }

            String json = Files.readString(metricsPath, StandardCharsets.UTF_8);
            Map<String, Object> metrics = io.lumina.agent.util.JsonUtils.OBJECT_MAPPER.readValue(json, Map.class);

            // 按 type 过滤返回
            if (type != null && !"all".equalsIgnoreCase(type)) {
                Map<String, Object> filtered = new HashMap<>();
                filtered.put("timestamp", metrics.get("timestamp"));
                filtered.put("host", metrics.get("host"));
                filtered.put(type.toLowerCase(), metrics.get(type.toLowerCase()));
                result.put("success", true);
                result.put("data", filtered);
            } else {
                result.put("success", true);
                result.put("data", metrics);
            }

        } catch (IOException e) {
            log.error("读取指标失败: type={}, error={}", type, e.getMessage());
            result.put("success", false);
            result.put("error", "读取指标失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 执行只读诊断命令
     *
     * <p>安全限制：仅允许白名单内的只读命令（ps/df/free/uptime 等），
     * 禁止任何修改操作。
     *
     * @param command 完整命令行（如 "df -h" / "free -m"）
     * @return 命令输出
     */
    @AgentTool(
            name = "ops.executeCommand",
            description = "执行只读诊断命令（仅允许白名单：ps/df/free/uptime/netstat/ls/cat/head/tail/wc）。返回命令输出。",
            category = "ops.diagnostic"
    )
    public Map<String, Object> executeCommand(String command) {
        log.info("Agent 调用运维工具 executeCommand: {}", command);

        Map<String, Object> result = new HashMap<>();
        if (command == null || command.isBlank()) {
            result.put("success", false);
            result.put("error", "命令不能为空");
            return result;
        }

        String[] parts = command.trim().split("\\s+");
        String cmd = parts[0];

        // 安全检查：只允许白名单命令
        if (!ALLOWED_COMMANDS.contains(cmd)) {
            result.put("success", false);
            result.put("error", "命令不在白名单中: " + cmd + "。允许的命令: " + ALLOWED_COMMANDS);
            return result;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(parts);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            int exitCode = finished ? process.exitValue() : -1;
            if (!finished) {
                process.destroyForcibly();
            }

            // 截断输出
            if (output.length() > 8000) {
                output = output.substring(0, 8000) + "\n... (输出已截断)";
            }

            result.put("success", exitCode == 0);
            result.put("exitCode", exitCode);
            result.put("output", output);

        } catch (Exception e) {
            log.error("执行命令失败: {}, error={}", command, e.getMessage());
            result.put("success", false);
            result.put("error", "执行命令失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 从 Nginx 日志行中提取 HTTP 状态码
     */
    private String extractHttpStatus(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        // 格式: ... "METHOD PATH HTTP/1.1" 200 1234 ...
        int lastQuote = line.lastIndexOf('"');
        if (lastQuote < 0) {
            return null;
        }
        String beforeQuote = line.substring(0, lastQuote).trim();
        String[] tokens = beforeQuote.split("\\s+");
        // 倒数第二个 token 通常是状态码
        if (tokens.length >= 2) {
            String candidate = tokens[tokens.length - 1];
            if (candidate.matches("\\d{3}")) {
                return candidate;
            }
        }
        return null;
    }
}
