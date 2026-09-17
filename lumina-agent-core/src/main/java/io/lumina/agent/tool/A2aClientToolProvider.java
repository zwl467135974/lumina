package io.lumina.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A2A（Agent2Agent）客户端工具提供者
 *
 * <p>让 Lumina Agent 以 A2A 开放协议调用外部远端 Agent：发现（getAgentCard）
 * 与委派（callAgent = message/send + tasks/get 轮询直到终态）。
 *
 * <p>SSRF 防护：默认拒绝私网/环回地址的目标（可经
 * {@code lumina.agent.a2a.allow-private-hosts=true} 放行，仅建议内网部署使用）。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Component
public class A2aClientToolProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 单次 HTTP 请求超时（message/send 提交通常很快） */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @Value("${lumina.agent.a2a.allow-private-hosts:false}")
    private boolean allowPrivateHosts;

    @Value("${lumina.agent.a2a.poll-interval-ms:2000}")
    private long pollIntervalMs;

    @Value("${lumina.agent.a2a.poll-timeout-seconds:120}")
    private long pollTimeoutSeconds;

    /**
     * 获取远端 A2A Agent Card（发现：确认对方能力后再委派）
     */
    @AgentTool(
        name = "a2a.getAgentCard",
        description = "获取远端 A2A Agent 的能力卡片（名称、描述、技能声明），用于在委派任务前确认对方能力。参数 url 为对方 Agent 的 JSON-RPC 端点地址（卡片从 {url}/../card 获取）。",
        category = "a2a"
    )
    public String getAgentCard(String url) {
        try {
            return executeCard(url);
        } catch (Exception e) {
            return "Error: 获取 Agent Card 失败: " + e.getMessage();
        }
    }

    /**
     * 调用远端 A2A Agent（message/send 提交 + tasks/get 轮询直到终态）
     *
     * @param agentUrl 远端 Agent 的 JSON-RPC 端点（Agent Card 的 url 字段）
     * @param message  要委派的任务文本
     * @param apiKey   可选的 Bearer 凭据（远端要求认证时使用）
     */
    @AgentTool(
        name = "a2a.callAgent",
        description = "通过 A2A 协议把任务委派给远端 Agent 并等待结果。参数：agentUrl（对方 Agent Card 中的 url，JSON-RPC 端点）、message（任务文本）、apiKey（可选，对方要求 Bearer 认证时提供）。",
        category = "a2a"
    )
    public String callAgent(String agentUrl, String message, String apiKey) {
        if (agentUrl == null || agentUrl.isBlank() || message == null || message.isBlank()) {
            return "Error: agentUrl 与 message 不能为空";
        }
        try {
            String endpoint = validateEndpoint(agentUrl);

            // 1) message/send 提交任务
            ObjectNode sendBody = rpcBody("message/send");
            ObjectNode a2aMessage = sendBody.putObject("params").putObject("message");
            a2aMessage.put("role", "user");
            a2aMessage.put("messageId", UUID.randomUUID().toString());
            a2aMessage.putArray("parts").addObject().put("type", "text").put("text", message);
            JsonNode sendResult = executeRpc(endpoint, apiKey, sendBody);
            JsonNode task = sendResult.path("result");
            String taskId = task.path("id").asText("");
            String state = task.path("status").path("state").asText("");
            if (taskId.isEmpty()) {
                return "Error: 对方未返回任务 ID: " + compact(sendResult);
            }

            // 2) 已终态直接返回，否则轮询 tasks/get
            long deadline = System.currentTimeMillis() + pollTimeoutSeconds * 1000;
            while (!isTerminal(state)) {
                if (System.currentTimeMillis() >= deadline) {
                    return "Error: 等待远端 Agent 超时（" + pollTimeoutSeconds + "s），任务 ID: " + taskId + "，可稍后用 tasks/get 查询";
                }
                Thread.sleep(pollIntervalMs);
                ObjectNode getBody = rpcBody("tasks/get");
                getBody.putObject("params").put("id", taskId);
                task = executeRpc(endpoint, apiKey, getBody).path("result");
                state = task.path("status").path("state").asText("");
            }

            if ("completed".equals(state)) {
                return extractText(task);
            }
            String failureMessage = task.path("status").path("message").asText("");
            return "Error: 远端任务未完成（state=" + state + (failureMessage.isEmpty() ? "" : ", message=" + failureMessage) + "）";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: 调用被中断";
        } catch (Exception e) {
            log.warn("A2A 调用失败: url={}, error={}", agentUrl, e.getMessage());
            return "Error: A2A 调用失败: " + e.getMessage();
        }
    }

    // ==================== 私有方法 ====================

    /** 探测 Agent Card：优先 {endpoint 父路径}/card */
    private String executeCard(String url) throws Exception {
        String endpoint = validateEndpoint(url);
        String cardUrl = endpoint.replaceAll("/+$", "") + "/../card";
        HttpRequest request = buildRequest(cardUrl, null, null);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return "Error: 获取卡片失败 HTTP " + response.statusCode();
        }
        JsonNode card = OBJECT_MAPPER.readTree(response.body());
        StringBuilder sb = new StringBuilder();
        sb.append("name: ").append(card.path("name").asText()).append('\n');
        sb.append("description: ").append(card.path("description").asText()).append('\n');
        sb.append("url: ").append(card.path("url").asText()).append('\n');
        for (JsonNode skill : card.path("skills")) {
            sb.append("skill: ").append(skill.path("name").asText())
                    .append(" - ").append(skill.path("description").asText()).append('\n');
        }
        return sb.toString();
    }

    private JsonNode executeRpc(String endpoint, String apiKey, ObjectNode body) throws Exception {
        HttpRequest request = buildRequest(endpoint, apiKey, body);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = OBJECT_MAPPER.readTree(response.body());
        JsonNode error = json.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new IllegalStateException("JSON-RPC 错误 " + error.path("code").asInt()
                    + ": " + error.path("message").asText());
        }
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return json;
    }

    private HttpRequest buildRequest(String url, String apiKey, ObjectNode jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }
        if (jsonBody != null) {
            builder.POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(jsonBody)));
        } else {
            builder.GET();
        }
        return builder.build();
    }

    private static ObjectNode rpcBody(String method) {
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", UUID.randomUUID().toString());
        body.put("method", method);
        return body;
    }

    /** 校验端点：仅 http/https，默认拒绝私网/环回目标（SSRF 防护） */
    private String validateEndpoint(String url) throws Exception {
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("仅支持 http/https 端点");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("端点缺少 host");
        }
        if (!allowPrivateHosts) {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                    throw new IllegalArgumentException(
                            "目标为主机私有地址，默认禁止（内网联调可配置 lumina.agent.a2a.allow-private-hosts=true）: "
                                    + uri.getHost());
                }
            }
        }
        return uri.toString();
    }

    private static boolean isTerminal(String state) {
        return "completed".equals(state) || "failed".equals(state)
                || "canceled".equals(state) || "rejected".equals(state);
    }

    /** completed 任务：拼接全部文本 Artifact */
    private static String extractText(JsonNode task) {
        List<String> texts = new ArrayList<>();
        for (JsonNode artifact : task.path("artifacts")) {
            for (JsonNode part : artifact.path("parts")) {
                String text = part.path("text").asText("");
                if (!text.isEmpty()) {
                    texts.add(text);
                }
            }
        }
        return texts.isEmpty() ? "(远端任务完成，但未返回文本产出)" : String.join("\n\n", texts);
    }

    private static String compact(JsonNode node) {
        String text = node.toString();
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
