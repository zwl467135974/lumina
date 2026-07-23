package io.lumina.agent.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.api.dto.openai.ChatCompletionChunk;
import io.lumina.agent.api.dto.openai.ChatCompletionRequest;
import io.lumina.agent.api.dto.openai.ChatCompletionResponse;
import io.lumina.agent.api.dto.openai.ModelListResponse;
import io.lumina.agent.service.OpenAiCompatService;
import io.lumina.common.exception.BaseException;
import io.lumina.framework.audit.annotation.Audit;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * OpenAI 兼容 Controller
 *
 * <p>标准 OpenAI SDK 可直接对接：{@code base_url=http://lumina-host/v1}，
 * {@code api_key=sk-xxx}（Gateway 的 ApiTokenAuthGlobalFilter 负责认证）。
 *
 * <p>流式与非流式共用 {@code /chat/completions}（由请求体 stream 字段区分），
 * 响应手动写出以严格遵循 OpenAI 线格式（SSE 为 {@code data: {chunk}\n\n}，
 * 结尾 {@code data: [DONE]\n\n}）；错误统一为 OpenAI 风格 error JSON。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@Tag(name = "OpenAI 兼容", description = "OpenAI SDK 兼容入口：/v1/chat/completions 与 /v1/models")
@RestController
@RequestMapping("/v1")
@Validated
@RequiredArgsConstructor
public class OpenAiCompatController {

    private final OpenAiCompatService openAiCompatService;

    private final ObjectMapper objectMapper;

    /**
     * Chat Completions（OpenAI 兼容，stream 字段区分流式/非流式）
     */
    @Audit(module = "agent", action = "EXECUTE", description = "OpenAI兼容执行Agent")
    @Operation(summary = "Chat Completions（OpenAI 兼容）")
    @PostMapping("/chat/completions")
    public void chatCompletions(@Valid @RequestBody ChatCompletionRequest request,
                                HttpServletResponse response) throws IOException {
        log.info("OpenAI 兼容执行: model={}, stream={}, messageCount={}",
                request.getModel(), request.getStream(),
                request.getMessages() != null ? request.getMessages().size() : 0);

        if (Boolean.TRUE.equals(request.getStream())) {
            writeStream(request, response);
        } else {
            writeCompletion(request, response);
        }
    }

    /**
     * 模型列表（当前租户的 Agent 伪装成 OpenAI model）
     */
    @Operation(summary = "模型列表（Agent 伪装为 OpenAI model）")
    @GetMapping("/models")
    public ModelListResponse listModels() {
        return openAiCompatService.listModels();
    }

    /**
     * 非流式：JSON 响应
     */
    private void writeCompletion(ChatCompletionRequest request, HttpServletResponse response) throws IOException {
        try {
            ChatCompletionResponse result = openAiCompatService.execute(request);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), result);
        } catch (Exception e) {
            writeError(response, e);
        }
    }

    /**
     * 流式：SSE 响应（data: {chunk}\n\n ... data: [DONE]\n\n）
     */
    private void writeStream(ChatCompletionRequest request, HttpServletResponse response) throws IOException {
        Flux<ChatCompletionChunk> flux;
        try {
            flux = openAiCompatService.executeStream(request);
        } catch (Exception e) {
            // 流建立前的错误（model 不存在、限流、预算等）→ OpenAI error JSON
            writeError(response, e);
            return;
        }

        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        PrintWriter writer = response.getWriter();

        try {
            for (ChatCompletionChunk chunk : flux.toIterable()) {
                writer.write("data: " + objectMapper.writeValueAsString(chunk) + "\n\n");
                writer.flush();
            }
        } catch (Exception e) {
            // 流中断：响应头已发出，只能以 SSE 事件形式带出错误
            log.error("OpenAI 兼容流式执行中断: model={}, error={}", request.getModel(), e.getMessage(), e);
            writer.write("data: " + objectMapper.writeValueAsString(
                    errorBody(resolveMessage(e), "server_error", "internal_error")) + "\n\n");
        }
        writer.write("data: [DONE]\n\n");
        writer.flush();
    }

    /**
     * 错误 → OpenAI 风格 error JSON（HTTP 状态取 BaseException.code，其余 500）
     */
    private void writeError(HttpServletResponse response, Exception e) throws IOException {
        int status = e instanceof BaseException be && be.getCode() != null ? be.getCode() : 500;
        String type = status >= 500 ? "server_error" : "invalid_request_error";
        log.warn("OpenAI 兼容请求失败: status={}, error={}", status, e.getMessage());

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                errorBody(resolveMessage(e), type, status >= 500 ? "internal_error" : "invalid_request"));
    }

    private Map<String, Object> errorBody(String message, String type, String code) {
        return Map.of("error", Map.of(
                "message", message != null ? message : "unknown error",
                "type", type,
                "code", code
        ));
    }

    private String resolveMessage(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
