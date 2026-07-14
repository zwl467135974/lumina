package io.lumina.agent.tool;

import io.lumina.agent.tool.search.SearchProvider;
import io.lumina.agent.tool.search.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用工具提供者
 *
 * <p>为 Agent 提供网络请求、时间查询、数学计算等通用能力。
 * 使用 JDK 21 内置 HttpClient，无需额外依赖。
 *
 * <p>位于 agent-core 模块，确保所有业务服务（base/agent/gateway）
 * 均可通过 Spring 组件扫描加载此工具。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeneralToolProvider {

    private static final int HTTP_TIMEOUT_SECONDS = 30;
    private static final int MAX_RESPONSE_LENGTH = 10000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    /**
     * 搜索 Provider（可选，配置 lumina.agent.search.provider 后自动注入）
     */
    @Autowired(required = false)
    private SearchProvider searchProvider;

    // ==================== HTTP 请求工具 ====================

    /**
     * 发送 HTTP 请求
     *
     * <p>Agent 可通过此工具调用外部 API 获取数据。
     * 支持 GET 和 POST 方法。
     */
    @AgentTool(
        name = "util.httpRequest",
        description = "发送HTTP请求获取外部数据。支持GET和POST方法。返回响应状态码和响应体（超过10000字符会截断）。",
        category = "util.http"
    )
    public Map<String, Object> httpRequest(String url, String method, String body, String headers) {
        log.info("Agent 调用 HTTP 请求工具: url={}, method={}", url, method);

        Map<String, Object> result = new HashMap<>();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(HTTP_TIMEOUT_SECONDS));

            // 解析 headers（JSON 格式：{"Content-Type":"application/json","Authorization":"Bearer xxx"}）
            if (headers != null && !headers.isBlank() && !headers.equals("{}")) {
                Map<String, String> headerMap = parseHeaders(headers);
                for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }

            // 设置请求方法和 body
            String upperMethod = method != null ? method.toUpperCase() : "GET";
            if ("POST".equals(upperMethod)) {
                builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            } else if ("PUT".equals(upperMethod)) {
                builder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            } else if ("DELETE".equals(upperMethod)) {
                builder.DELETE();
            } else {
                builder.GET();
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();
            if (responseBody != null && responseBody.length() > MAX_RESPONSE_LENGTH) {
                responseBody = responseBody.substring(0, MAX_RESPONSE_LENGTH)
                        + "\n... (已截断，完整响应 " + responseBody.length() + " 字符)";
            }

            result.put("success", true);
            result.put("statusCode", response.statusCode());
            result.put("body", responseBody);
            return result;

        } catch (Exception e) {
            log.error("HTTP 请求失败: url={}, error={}", url, e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    // ==================== 时间查询工具 ====================

    /**
     * 获取当前时间
     *
     * <p>Agent 可通过此工具获取当前日期时间，用于回答"现在几点"等问题。
     */
    @AgentTool(
        name = "util.getCurrentTime",
        description = "获取当前日期和时间。返回标准格式的时间字符串和Unix时间戳。用于回答时间相关的问题。",
        category = "util.time"
    )
    public Map<String, Object> getCurrentTime() {
        log.info("Agent 调用时间查询工具");

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("datetime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("date", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        result.put("time", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        result.put("dayOfWeek", now.getDayOfWeek().toString());
        result.put("timezone", "Asia/Shanghai (UTC+8)");
        result.put("timestamp", now.atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond());
        return result;
    }

    // ==================== 网络搜索工具 ====================

    /**
     * 网络搜索
     *
     * <p>Agent 可通过此工具搜索互联网信息。
     * 支持智谱/Tavily/SerpAPI/Brave 四种搜索引擎，
     * 通过 lumina.agent.search.provider 配置选择，配置 api-key 后即可使用。
     */
    @AgentTool(
        name = "util.webSearch",
        description = "搜索互联网获取实时信息。输入搜索关键词，返回标题、链接和摘要。支持智谱/Tavily/SerpAPI/Brave搜索引擎。",
        category = "util.search"
    )
    public Map<String, Object> webSearch(String query) {
        log.info("Agent 调用网络搜索工具: query={}", query);

        Map<String, Object> result = new HashMap<>();

        if (searchProvider == null) {
            result.put("success", false);
            result.put("error", "网络搜索未配置。请在 Nacos 或环境变量中设置 lumina.agent.search.provider（zhipu/tavily/serpapi/brave）和 lumina.agent.search.api-key。");
            return result;
        }

        try {
            List<SearchResult> results = searchProvider.search(query, 10);

            result.put("success", true);
            result.put("provider", searchProvider.getProviderName());
            result.put("count", results.size());
            result.put("results", results);
            return result;

        } catch (Exception e) {
            log.error("网络搜索失败: query={}, provider={}, error={}",
                    query, searchProvider.getProviderName(), e.getMessage());
            result.put("success", false);
            result.put("provider", searchProvider.getProviderName());
            result.put("error", "搜索失败: " + e.getMessage());
            return result;
        }
    }

    // ==================== 数学计算工具 ====================

    /**
     * 数学表达式计算
     *
     * <p>支持四则运算（+、-、*、/）和括号，使用 BigDecimal 保证精度。
     * 不使用 eval/scriptEngine，安全无注入风险。
     */
    @AgentTool(
        name = "util.calculate",
        description = "计算数学表达式。支持加减乘除（+-*/）和括号。使用高精度计算，避免浮点误差。例如：calculate('3.14 * 10 * 10', '2')。",
        category = "util.math"
    )
    public Map<String, Object> calculate(String expression, String decimalPlaces) {
        log.info("Agent 调用计算工具: expression={}", expression);

        Map<String, Object> result = new HashMap<>();
        try {
            BigDecimal value = evaluateExpression(expression);
            int scale = decimalPlaces != null && !decimalPlaces.isBlank()
                    ? Integer.parseInt(decimalPlaces.trim())
                    : 6;
            value = value.setScale(scale, RoundingMode.HALF_UP);

            result.put("success", true);
            result.put("expression", expression);
            result.put("result", value.toPlainString());
            return result;

        } catch (NumberFormatException e) {
            result.put("success", false);
            result.put("error", "数字格式错误: " + e.getMessage());
            return result;
        } catch (Exception e) {
            log.error("计算失败: expression={}, error={}", expression, e.getMessage());
            result.put("success", false);
            result.put("error", "表达式计算失败: " + e.getMessage());
            return result;
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 安全解析 headers JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseHeaders(String headersJson) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(headersJson, Map.class);
        } catch (Exception e) {
            log.warn("解析 headers 失败,忽略: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 安全的数学表达式求值（递归下降，仅支持 + - * / 和括号）
     *
     * <p>不使用 ScriptEngine/eval，避免代码注入。
     */
    private BigDecimal evaluateExpression(String expr) {
        if (expr == null || expr.isBlank()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        // 清理空格
        expr = expr.replaceAll("\\s+", "");
        return new ExprParser(expr).parse();
    }

    /**
     * 递归下降解析器（线程安全，无副作用）
     */
    private static class ExprParser {
        private final String input;
        private int pos = 0;

        ExprParser(String input) {
            this.input = input;
        }

        BigDecimal parse() {
            BigDecimal result = parseExpression();
            if (pos < input.length()) {
                throw new IllegalArgumentException("无法解析的字符: " + input.charAt(pos));
            }
            return result;
        }

        private BigDecimal parseExpression() {
            BigDecimal left = parseTerm();
            while (pos < input.length()) {
                char op = input.charAt(pos);
                if (op == '+') {
                    pos++;
                    left = left.add(parseTerm());
                } else if (op == '-') {
                    pos++;
                    left = left.subtract(parseTerm());
                } else {
                    break;
                }
            }
            return left;
        }

        private BigDecimal parseTerm() {
            BigDecimal left = parseFactor();
            while (pos < input.length()) {
                char op = input.charAt(pos);
                if (op == '*') {
                    pos++;
                    left = left.multiply(parseFactor());
                } else if (op == '/') {
                    pos++;
                    BigDecimal right = parseFactor();
                    if (right.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ArithmeticException("除以零");
                    }
                    left = left.divide(right, MathContext.DECIMAL128);
                } else {
                    break;
                }
            }
            return left;
        }

        private BigDecimal parseFactor() {
            if (pos < input.length() && input.charAt(pos) == '(') {
                pos++; // 跳过 (
                BigDecimal result = parseExpression();
                if (pos >= input.length() || input.charAt(pos) != ')') {
                    throw new IllegalArgumentException("缺少右括号");
                }
                pos++; // 跳过 )
                return result;
            }
            if (pos < input.length() && input.charAt(pos) == '-') {
                pos++;
                return parseFactor().negate();
            }
            return parseNumber();
        }

        private BigDecimal parseNumber() {
            int start = pos;
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (Character.isDigit(c) || c == '.') {
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) {
                throw new IllegalArgumentException("期望数字，但在位置 " + pos + " 遇到: "
                        + (pos < input.length() ? input.charAt(pos) : "结束"));
            }
            return new BigDecimal(input.substring(start, pos));
        }
    }
}
