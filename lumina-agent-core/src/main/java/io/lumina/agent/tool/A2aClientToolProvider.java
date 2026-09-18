package io.lumina.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.lumina.agent.security.ExternalContentSanitizer;
import io.lumina.agent.util.InetAddresses;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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
 * <p>SSRF 防护（双层）：
 * <ul>
 *   <li>请求前快速校验 {@link #validateEndpoint(String)}：scheme/host 合法性 +
 *       私网地址判定（fail-fast，给出可读错误）；</li>
 *   <li>连接时权威校验 {@link ValidatingDnsResolver}：Apache HttpClient 建连
 *       时经自定义 DnsResolver 解析，解析出的每个地址都过同一份私网判定，
 *       未通过即拒连——校验与连接使用同一次解析，从构造上消除"校验后、
 *       连接前再次解析"的 TOCTOU / DNS rebinding 缝隙。重定向默认不跟随。</li>
 * </ul>
 * 可经 {@code lumina.agent.a2a.allow-private-hosts=true} 放行（仅建议内网
 * 部署使用，同时作用于两层校验）。
 *
 * <p>出口安检：外部 Agent 的返回文本（与 Agent Card 内容）进入本方模型上下文
 * 前经 {@link ExternalContentSanitizer} 安检（可选装配，未注册实现时放行）——
 * 外部产出 = 外部可控的提示注入源。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Component
public class A2aClientToolProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 单次 HTTP 请求超时（message/send 提交通常很快） */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @Value("${lumina.agent.a2a.allow-private-hosts:false}")
    private boolean allowPrivateHosts;

    @Value("${lumina.agent.a2a.poll-interval-ms:2000}")
    private long pollIntervalMs;

    @Value("${lumina.agent.a2a.poll-timeout-seconds:120}")
    private long pollTimeoutSeconds;

    /** 外部内容安检（业务层可选装配，缺失时放行——与 Sink 扩展模式一致） */
    @Autowired(required = false)
    private ExternalContentSanitizer contentSanitizer;

    /** 连接时校验的传输客户端（校验逻辑见 {@link ValidatingDnsResolver}） */
    private final CloseableHttpClient httpClient = buildHttpClient();

    private CloseableHttpClient buildHttpClient() {
        // DnsResolver 挂在连接管理器上：classic HttpClientBuilder 无此入口，
        // 连接时解析 → ValidatingDnsResolver 校验 → 校验与连接同一次解析
        org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager connectionManager =
                org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new ValidatingDnsResolver())
                        .build();
        return HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .disableRedirectHandling()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(10))
                        .setResponseTimeout(Timeout.ofSeconds(REQUEST_TIMEOUT.toSeconds()))
                        .build())
                .build();
    }

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
                return screened(extractText(task), agentUrl);
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

    /** 探测 Agent Card：优先 {endpoint 父路径}/card（内容同样过出口安检） */
    private String executeCard(String url) throws Exception {
        String endpoint = validateEndpoint(url);
        String cardUrl = endpoint.replaceAll("/+$", "") + "/../card";
        String body = executeForBody(ClassicRequestBuilder.get(cardUrl).build(), null);
        JsonNode card = OBJECT_MAPPER.readTree(body);
        StringBuilder sb = new StringBuilder();
        sb.append("name: ").append(card.path("name").asText()).append('\n');
        sb.append("description: ").append(card.path("description").asText()).append('\n');
        sb.append("url: ").append(card.path("url").asText()).append('\n');
        for (JsonNode skill : card.path("skills")) {
            sb.append("skill: ").append(skill.path("name").asText())
                    .append(" - ").append(skill.path("description").asText()).append('\n');
        }
        return screened(sb.toString(), url);
    }

    private JsonNode executeRpc(String endpoint, String apiKey, ObjectNode body) throws Exception {
        ClassicRequestBuilder builder = ClassicRequestBuilder.post(endpoint)
                .setHeader("Content-Type", "application/json")
                .setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body),
                        org.apache.hc.core5.http.ContentType.APPLICATION_JSON));
        String responseBody = executeForBody(builder.build(), apiKey);
        JsonNode json = OBJECT_MAPPER.readTree(responseBody);
        JsonNode error = json.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new IllegalStateException("JSON-RPC 错误 " + error.path("code").asInt()
                    + ": " + error.path("message").asText());
        }
        return json;
    }

    /** 执行请求并断言 HTTP 2xx，返回响应体 */
    private String executeForBody(org.apache.hc.core5.http.ClassicHttpRequest request,
                                  String apiKey) throws Exception {
        if (apiKey != null && !apiKey.isBlank()) {
            request.setHeader("Authorization", "Bearer " + apiKey.trim());
        }
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String body = response.getEntity() == null
                    ? "" : EntityUtils.toString(response.getEntity());
            int code = response.getCode();
            if (code >= 400) {
                throw new IllegalStateException("HTTP " + code + (body.isEmpty() ? ""
                        : ": " + compact(OBJECT_MAPPER.readTree(body))));
            }
            return body;
        }
    }

    /** 出口安检：外部内容进入本方模型上下文前的统一收口 */
    private String screened(String content, String sourceUrl) {
        if (contentSanitizer == null) {
            return content;
        }
        return contentSanitizer.sanitize(content, "a2a:" + sourceUrl);
    }

    /**
     * 请求前快速校验：仅 http/https，默认拒绝私网/环回目标（fail-fast）
     *
     * <p>连接时的权威校验在 {@link ValidatingDnsResolver}——两层共用
     * {@link InetAddresses#isPrivateOrLocal(InetAddress)} 谓词。
     */
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
                if (InetAddresses.isPrivateOrLocal(address)) {
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

    private static ObjectNode rpcBody(String method) {
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", UUID.randomUUID().toString());
        body.put("method", method);
        return body;
    }

    private static String compact(JsonNode node) {
        String text = node.toString();
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    /**
     * 连接时校验的 DNS 解析器（SSRF 权威防线）
     *
     * <p>Apache HttpClient 建连时调用本解析器：先做系统解析，再对每个地址
     * 过 {@link InetAddresses#isPrivateOrLocal(InetAddress)} 判定，未通过即
     * 抛 {@link UnknownHostException} 拒绝建连。校验与连接使用同一次解析，
     * DNS rebinding（校验时返回公网 IP、连接时返回私网 IP）无法绕过。
     * 允许私网（allow-private-hosts=true）时退化为系统解析。
     */
    class ValidatingDnsResolver implements DnsResolver {

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
            if (allowPrivateHosts) {
                return addresses;
            }
            for (InetAddress address : addresses) {
                if (InetAddresses.isPrivateOrLocal(address)) {
                    log.warn("SSRF 防护：连接时解析到私有地址，拒绝建连: host={}, addr={}",
                            host, address.getHostAddress());
                    throw new UnknownHostException(
                            "目标解析到私有地址，已拒绝连接（SSRF 防护）: " + host);
                }
            }
            return addresses;
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
        }
    }
}
