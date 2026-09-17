package io.lumina.agent.api.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * A2A JSON-RPC 2.0 请求/响应
 *
 * <p>请求：{@code POST /v1/a2a/agents/{agentId}}，method 支持 message/send、
 * tasks/get、tasks/cancel；params 原样保留为 JsonNode 由服务层解析。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
public class A2aJsonRpc {

    private A2aJsonRpc() {
    }

    @Data
    public static class Request {
        private String jsonrpc;
        /** 协议要求字符串 "2.0"，宽松兼容 */
        private Object id;
        private String method;
        private JsonNode params;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Response {
        private String jsonrpc = "2.0";
        private Object id;
        private Object result;
        private RpcError error;

        public static Response ok(Object id, Object result) {
            Response response = new Response();
            response.setId(id);
            response.setResult(result);
            return response;
        }

        public static Response error(Object id, int code, String message) {
            Response response = new Response();
            response.setId(id);
            response.setError(new RpcError(code, message));
            return response;
        }
    }

    /** JSON-RPC 标准错误码：-32700 解析错误 / -32600 无效请求 / -32601 方法不存在 / -32602 参数无效 / -32000 服务端业务错误 */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RpcError {
        private int code;
        private String message;

        public RpcError() {
        }

        public RpcError(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
