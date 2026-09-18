package io.lumina.agent.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.api.dto.a2a.A2aAgentCard;
import io.lumina.agent.api.dto.a2a.A2aJsonRpc;
import io.lumina.agent.service.A2aService;
import io.lumina.common.exception.BaseException;
import io.lumina.framework.audit.annotation.Audit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * A2A（Agent2Agent）开放协议入口
 *
 * <p>与 OpenAI 兼容出口（/v1）并列的对外协议面，认证复用 Gateway/Standalone
 * 的 API Token（Bearer sk-xxx）：外部 A2A 客户端以
 * {@code base_url=http://lumina-host/v1/a2a} 对接。
 *
 * <p>发现：{@code GET /v1/a2a/agents}（卡片列表）、
 * {@code GET /v1/a2a/agents/{agentId}/card}；
 * 任务：{@code POST /v1/a2a/agents/{agentId}}（JSON-RPC 2.0，
 * method=message/send / tasks/get / tasks/cancel）。
 * JSON-RPC 业务错误随 HTTP 200 返回（-32000，参数类为 -32602）。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Tag(name = "A2A 协议", description = "Agent2Agent 开放协议：Agent Card 发现 + JSON-RPC 任务")
@RestController
@RequestMapping("/v1/a2a")
@Validated
@RequiredArgsConstructor
public class A2aController {

    private static final String METHOD_MESSAGE_SEND = "message/send";

    private static final String METHOD_MESSAGE_STREAM = "message/stream";

    private static final String METHOD_TASKS_GET = "tasks/get";

    private static final String METHOD_TASKS_CANCEL = "tasks/cancel";

    private final A2aService a2aService;

    private final ObjectMapper objectMapper;

    @Operation(summary = "A2A Agent 卡片列表（发现）")
    @GetMapping("/agents")
    public List<A2aAgentCard> listAgentCards(HttpServletRequest request) {
        return a2aService.listAgentCards(baseUrl(request));
    }

    @Operation(summary = "A2A Agent Card（单 Agent 发现）")
    @GetMapping("/agents/{agentId}/card")
    public A2aAgentCard getAgentCard(@PathVariable("agentId") Long agentId, HttpServletRequest request) {
        return a2aService.getAgentCard(agentId, baseUrl(request));
    }

    @Audit(module = "a2a", action = "EXECUTE", description = "A2A JSON-RPC 调用")
    @Operation(summary = "A2A JSON-RPC 端点（message/send、message/stream、tasks/get、tasks/cancel）")
    @PostMapping("/agents/{agentId}")
    public Object jsonRpc(@PathVariable("agentId") Long agentId,
                          @Valid @RequestBody A2aJsonRpc.Request rpcRequest) {
        String method = rpcRequest.getMethod() == null ? "" : rpcRequest.getMethod();
        if (METHOD_MESSAGE_STREAM.equals(method)) {
            return messageStreamSse(agentId, rpcRequest);
        }
        try {
            Object result = switch (method) {
                case METHOD_MESSAGE_SEND -> a2aService.messageSend(agentId, rpcRequest.getParams());
                case METHOD_TASKS_GET -> a2aService.taskGet(rpcRequest.getParams());
                case METHOD_TASKS_CANCEL -> a2aService.taskCancel(rpcRequest.getParams());
                default -> null;
            };
            if (result == null) {
                return A2aJsonRpc.Response.error(rpcRequest.getId(), -32601,
                        "Method not found: " + rpcRequest.getMethod());
            }
            return A2aJsonRpc.Response.ok(rpcRequest.getId(), result);
        } catch (BaseException e) {
            // 参数类业务异常映射 -32602，其余业务错误统一 -32000（保持 HTTP 200，错误走 JSON-RPC 体）
            int code = e.getCode() != null && e.getCode() == HttpStatus.BAD_REQUEST.value() ? -32602 : -32000;
            log.warn("A2A JSON-RPC 业务失败: method={}, code={}, error={}",
                    rpcRequest.getMethod(), e.getCode(), e.getMessage());
            return A2aJsonRpc.Response.error(rpcRequest.getId(), code, e.getMessage());
        } catch (Exception e) {
            log.error("A2A JSON-RPC 执行异常: method={}", rpcRequest.getMethod(), e);
            return A2aJsonRpc.Response.error(rpcRequest.getId(), -32000,
                    "Internal error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * message/stream：SSE 推送任务事件流（data 为 JSON-RPC 响应，result 为 A2A Task）
     *
     * <p>客户端需带 {@code Accept: text/event-stream}；流建立前的业务错误以
     * 首个错误事件下发（HTTP 200），流中异常降级为 failed 事件后结束。
     */
    private Object messageStreamSse(Long agentId, A2aJsonRpc.Request rpcRequest) {
        Object id = rpcRequest.getId();
        return a2aService.messageStream(agentId, rpcRequest.getParams())
                .<ServerSentEvent<String>>map(task -> ServerSentEvent
                        .builder(toJson(A2aJsonRpc.Response.ok(id, task)))
                        .build())
                .onErrorResume(e -> Flux.just(ServerSentEvent
                        .builder(toJson(errorResponse(id, e)))
                        .build()));
    }

    private String toJson(A2aJsonRpc.Response response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"serialize failed\"}}";
        }
    }

    private A2aJsonRpc.Response errorResponse(Object id, Throwable e) {
        int code = e instanceof BaseException be && be.getCode() != null
                && be.getCode() == HttpStatus.BAD_REQUEST.value() ? -32602 : -32000;
        return A2aJsonRpc.Response.error(id, code,
                e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
    }

    /**
     * 计算对外可见的 base URL（优先 X-Forwarded-*，网关部署时卡片 url 指向网关）
     */
    private String baseUrl(HttpServletRequest request) {
        String scheme = headerOrDefault(request, "X-Forwarded-Proto", request.getScheme());
        String host = headerOrDefault(request, "X-Forwarded-Host", null);
        if (host == null || host.isBlank()) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port > 0 && port != 80 && port != 443) {
                host = host + ":" + port;
            }
        }
        return scheme + "://" + host;
    }

    private String headerOrDefault(HttpServletRequest request, String header, String fallback) {
        String value = request.getHeader(header);
        return value == null || value.isBlank() ? fallback : value.split(",")[0].trim();
    }
}
