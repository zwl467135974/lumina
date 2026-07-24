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
import reactor.util.context.Context;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lumina 推理链 Tracer 实现
 *
 * <p>通过 {@code TracerRegistry.register()} 全局注册后，
 * 自动拦截 AgentScope 所有 Agent/Model/Tool 调用，采集推理链步骤。
 *
 * <p><b>上下文传递机制</b>：AgentScope 2.0 基于 Reactor 响应式链，
 * callModel/callTool 可能在不同线程执行。因此 TraceContext 必须通过
 * Reactor Context（{@code contextWrite}/{@code deferContextual}）传递，
 * 而非 ThreadLocal。
 *
 * <p>流程：
 * <ol>
 *   <li>{@link #callAgent} 创建 TraceContext，通过 contextWrite 写入 Reactor Context</li>
 *   <li>{@link #callModel}/{@link #callTool} 通过 deferContextual 从 Reactor Context 读取</li>
 * </ol>
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
        // 从 Reactor Context 取引擎层注入的 TraceContext
        return Mono.deferContextual(ctxView -> {
            TraceContext fromReactor = ctxView.getOrDefault(TraceContext.KEY, null);

            // Reactor Context 没有时，先查 ThreadLocal（PlanExecuteAgent 内部 .block() 同线程场景）
            TraceContext ctx = fromReactor != null ? fromReactor : collector.getCurrentContext();

            if (ctx == null) {
                // 兜底：引擎未启动 trace 且 ThreadLocal 也无（如独立 SDK 调用），Tracer 自行创建
                TraceContext newCtx = collector.startTrace(agent.getName());
                newCtx.setInputText(extractUserInput(inputs));
                return next.get()
                        .contextWrite(context -> context.put(TraceContext.KEY, newCtx));
            }

            // 补充 agent 名称（如果引擎未设置）
            if (ctx.getAgentName() == null) {
                ctx.setAgentName(agent.getName());
            }

            // 将已有 TraceContext 写入 Reactor Context，供下游 callModel/callTool 读取
            // （覆盖 PlanExecuteAgent 内部 agent.call() 未 contextWrite 的场景）
            final TraceContext finalCtx = ctx;
            return next.get()
                    .contextWrite(context -> context.put(TraceContext.KEY, finalCtx));
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

        // 从 Reactor Context 取 TraceContext（callAgent 注入的）
        return Flux.deferContextual(ctxView -> {
            TraceContext ctx = ctxView.getOrDefault(TraceContext.KEY, null);
            // 在订阅时创建 step（记录 startTimestamp = LLM 调用实际开始时刻）
            TraceStep step = collector.startReasoningStep(ctx);

            return next.get()
                    .doOnNext(response -> {
                        if (ctx == null || step == null) return;
                        ChatUsage usage = response.getUsage();
                        if (usage != null && usage.getTotalTokens() > 0) {
                            // finish() 计算 now - startTimestamp = LLM 调用实际耗时
                            collector.finishReasoningStep(ctx, step,
                                    usage.getInputTokens(), usage.getOutputTokens());
                        }
                    });
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

        // 从 Reactor Context 取 TraceContext
        return Mono.deferContextual(ctxView -> {
            TraceContext ctx = ctxView.getOrDefault(TraceContext.KEY, null);

            return next.get()
                    .doOnSuccess(result -> {
                        if (ctx == null) return;
                        long duration = System.currentTimeMillis() - start;
                        String output = "";
                        if (result != null && result.getOutput() != null) {
                            output = result.getOutput().toString();
                        }
                        collector.recordToolStep(ctx, finalToolName, finalInput,
                                TraceStep.truncate(output, 500), duration);
                    });
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
