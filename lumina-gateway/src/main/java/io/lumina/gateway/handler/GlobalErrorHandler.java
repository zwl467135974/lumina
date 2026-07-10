package io.lumina.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关全局异常处理器（WebFlux）
 *
 * <p>捕获网关层异常，返回与 {@link R} 一致的 JSON 错误响应。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Order(-1)
@Component
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        ErrorCode errorCode;
        String message;

        if (ex instanceof JwtException) {
            errorCode = ErrorCode.TOKEN_INVALID;
            message = ErrorCode.TOKEN_INVALID.getMessage();
        } else if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) {
                errorCode = ErrorCode.NOT_FOUND;
                message = "请求的资源或路由不存在";
            } else if (status == HttpStatus.UNAUTHORIZED) {
                errorCode = ErrorCode.UNAUTHORIZED;
                message = ErrorCode.UNAUTHORIZED.getMessage();
            } else if (status == HttpStatus.FORBIDDEN) {
                errorCode = ErrorCode.FORBIDDEN;
                message = ErrorCode.FORBIDDEN.getMessage();
            } else if (status == HttpStatus.REQUEST_TIMEOUT) {
                errorCode = ErrorCode.REQUEST_TIMEOUT;
                message = ErrorCode.REQUEST_TIMEOUT.getMessage();
            } else if (status == HttpStatus.TOO_MANY_REQUESTS) {
                errorCode = ErrorCode.TOO_MANY_REQUESTS;
                message = ErrorCode.TOO_MANY_REQUESTS.getMessage();
            } else if (status != null && status.is4xxClientError()) {
                errorCode = ErrorCode.BAD_REQUEST;
                message = rse.getReason() != null ? rse.getReason() : "请求参数错误";
            } else {
                errorCode = ErrorCode.INTERNAL_ERROR;
                message = "网关服务异常";
            }
        } else {
            errorCode = ErrorCode.INTERNAL_ERROR;
            message = "网关服务异常，请稍后重试";
        }

        log.warn("网关异常: path={}, errCode={}, msg={}, cause={}",
                exchange.getRequest().getURI().getPath(),
                errorCode.getCode(), message, ex.getMessage());

        response.setStatusCode(HttpStatus.valueOf(errorCode.getHttpStatus()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(R.fail(errorCode, message));
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception serializeEx) {
            log.error("序列化错误响应失败", serializeEx);
            return Mono.error(serializeEx);
        }
    }
}
