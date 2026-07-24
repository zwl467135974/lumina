package io.lumina.agent.tracing;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lumina 推理链 Tracer 实现
 *
 * <p>通过 {@code TracerRegistry.register()} 全局注册后，
 * 自动拦截 AgentScope 所有 Agent/Model/Tool 调用，采集推理链步骤。
 *
 * <p>不需要改任何 Builder 代码——一行注册全局生效。
 *
 * @author Lumina Team
 * @since 3.7.0
 */
@Slf4j
@RequiredArgsConstructor
public class LuminaTraceTracer implements Tracer {

    private final TraceCollector collector;

    // ==================== Agent 调用（创建 trace root）====================

    @Override
    public Mono<Msg> callAgent(AgentBase agent, List<Msg> inputs, Supplier<Mono<Msg>> next) {
        TraceContext ctx = collector.startTrace(agent.getName());

        // 提取用户最后一条消息作为 input
        String userInput = extractUserInput(inputs);
        ctx.setInputText(userInput);

        // 先获取 next Mono（此时 SDK 内部的 callModel/callTool 尚未执行）
        Mono<Msg> resultMono = next.get();

        // 在 next.get() 返回后、但 block() 完成前，callModel/callTool 已在同线程执行完
        // 所以 ThreadLocal 在同步 block 场景下是有效的
        return resultMono
                .doOnSuccess(result -> {
                    String output = result != null ? result.getTextContent() : null;
                    ctx.markSuccess(output);
                    collector.finishTrace(ctx);
                })
                .doOnError(error -> {
                    ctx.markFailed(error.getMessage());
                    collector.finishTrace(ctx);
                });
    }

    // ==================== LLM 调用（每轮 Reason）====================

    @Override
    public Flux<ChatResponse> callModel(
            io.agentscope.core.model.ChatModelBase model,
            List<Msg> inputs,
            List<io.agentscope.core.model.ToolSchema> tools,
            io.agentscope.core.model.GenerateOptions options,
            Supplier<Flux<ChatResponse>> next) {

        return next.get()
                .doOnNext(response -> {
                    ChatUsage usage = response.getUsage();
                    if (usage != null && usage.getTotalTokens() > 0) {
                        collector.recordReasoningStep(usage.getInputTokens(), usage.getOutputTokens());
                    }
                });
    }

    // ==================== 工具调用（每轮 Act）====================

    @Override
    public Mono<ToolResultBlock> callTool(Toolkit toolkit, ToolCallParam param,
                                           Supplier<Mono<ToolResultBlock>> next) {
        long start = System.currentTimeMillis();

        // 提取工具名和入参
        String toolName = "unknown";
        String input = "";
        if (param.getToolUseBlock() != null) {
            toolName = param.getToolUseBlock().getName();
        }
        Map<String, Object> inputMap = param.getInput();
        if (inputMap != null && !inputMap.isEmpty()) {
            input = inputMap.toString();
        }

        final String finalToolName = toolName;
        final String finalInput = TraceStep.truncate(input, 500);

        return next.get()
                .doOnSuccess(result -> {
                    long duration = System.currentTimeMillis() - start;
                    String output = "";
                    if (result != null && result.getOutput() != null) {
                        output = result.getOutput().toString();
                    }
                    collector.recordToolStep(finalToolName, finalInput,
                            TraceStep.truncate(output, 500), duration);
                });
    }

    // ==================== 辅助方法 ====================

    /**
     * 从消息列表中提取最后一条用户消息的文本
     */
    private String extractUserInput(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg msg = messages.get(i);
            if (msg.getRole() == io.agentscope.core.message.MsgRole.USER) {
                return msg.getTextContent();
            }
        }
        return messages.get(messages.size() - 1).getTextContent();
    }
}
