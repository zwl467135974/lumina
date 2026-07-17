package io.lumina.agent.service;

import io.lumina.agent.api.dto.openai.ChatCompletionChunk;
import io.lumina.agent.api.dto.openai.ChatCompletionRequest;
import io.lumina.agent.api.dto.openai.ChatCompletionResponse;
import io.lumina.agent.api.dto.openai.ModelListResponse;
import reactor.core.publisher.Flux;

/**
 * OpenAI 兼容服务接口
 *
 * <p>将标准 OpenAI Chat Completions 请求映射到 Lumina Agent 执行
 * （复用 {@link AgentService} 的预算/限流/审计/安全管线，禁止直调执行引擎）。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
public interface OpenAiCompatService {

    /**
     * 非流式执行
     *
     * @param request OpenAI 请求
     * @return OpenAI 响应（含 usage）
     */
    ChatCompletionResponse execute(ChatCompletionRequest request);

    /**
     * 流式执行（SSE chunk）
     *
     * @param request OpenAI 请求
     * @return chunk 流（结尾附 finish_reason=stop 的终止 chunk，[DONE] 由 Controller 发送）
     */
    Flux<ChatCompletionChunk> executeStream(ChatCompletionRequest request);

    /**
     * 模型列表（当前租户的 Agent 伪装成 OpenAI model）
     *
     * @return 模型列表
     */
    ModelListResponse listModels();
}
