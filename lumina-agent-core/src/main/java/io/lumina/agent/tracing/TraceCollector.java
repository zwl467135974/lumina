package io.lumina.agent.tracing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.common.core.BaseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Trace 采集器——收集步骤 + 异步落库
 *
 * <p>使用 ThreadLocal 存储当前线程的 TraceContext（同步路径），
 * Reactor Context 用于异步路径（由 LuminaTraceTracer 注入）。
 *
 * <p>落库通过 {@link TraceSink} 异步执行，不阻塞主路径。
 *
 * @author Lumina Team
 * @since 3.7.0
 */
@Slf4j
@Component
public class TraceCollector {

    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TraceSink traceSink;

    public TraceCollector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** ThreadLocal 存储当前 TraceContext（同步执行路径） */
    private static final ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();

    /**
     * 开始一次 Trace
     */
    public TraceContext startTrace(String agentName) {
        TraceContext ctx = new TraceContext();
        ctx.setAgentName(agentName);

        CURRENT.set(ctx);
        return ctx;
    }

    /**
     * 完成 Trace——异步落库
     */
    public void finishTrace(TraceContext ctx) {
        if (ctx == null) return;
        CURRENT.remove();  // 清理 ThreadLocal

        // traceSink 可能为 null（如 base 模块测试无 AgentTraceSink），跳过落库
        if (traceSink == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                traceSink.save(ctx);
            } catch (Exception e) {
                log.warn("Trace 落库失败（不影响主流程）: traceUuid={}, error={}",
                        ctx.getTraceUuid(), e.getMessage());
            }
        });
    }

    /**
     * 开始记录推理步骤（在 LLM 调用前调用，记录开始时间）
     *
     * @param ctx Trace 上下文
     * @return 创建的 TraceStep（尚未 finish，供后续 finishReasoningStep 使用）
     */
    public TraceStep startReasoningStep(TraceContext ctx) {
        if (ctx == null) return null;
        return ctx.newStep("REASONING", "LLM 推理");
    }

    /**
     * 完成推理步骤（在 LLM 返回 usage 后调用，计算实际耗时）
     *
     * @param ctx              Trace 上下文
     * @param step             startReasoningStep 返回的 step
     * @param promptTokens     输入 Token 数
     * @param completionTokens 输出 Token 数
     */
    public void finishReasoningStep(TraceContext ctx, TraceStep step, int promptTokens, int completionTokens) {
        if (ctx == null || step == null) return;
        step.setPromptTokens(promptTokens);
        step.setCompletionTokens(completionTokens);
        step.finish();
        ctx.addStep(step);
    }

    /**
     * 记录推理步骤（一次性完成，无耗时测量——兼容旧调用方）
     *
     * @param ctx              Trace 上下文
     * @param promptTokens     输入 Token 数
     * @param completionTokens 输出 Token 数
     */
    public void recordReasoningStep(TraceContext ctx, int promptTokens, int completionTokens) {
        if (ctx == null) return;
        TraceStep step = ctx.newStep("REASONING", "LLM 推理");
        step.setPromptTokens(promptTokens);
        step.setCompletionTokens(completionTokens);
        step.finish();
        ctx.addStep(step);
    }

    /**
     * 记录工具调用步骤
     *
     * @param ctx        Trace 上下文（从 Reactor Context 取得）
     */
    public void recordToolStep(TraceContext ctx, String toolName, String input, String output, long durationMs) {
        if (ctx == null) return;

        TraceStep step = ctx.newStep("TOOL_CALL", "工具: " + toolName);
        step.setInput(input);
        step.setOutput(output);
        step.setDurationMs(durationMs);
        ctx.addStep(step);
    }

    /**
     * 记录 RAG 检索步骤（手动埋点，从 ThreadLocal 取 ctx）
     */
    public void recordRetrievalStep(String query, int retrievedCount, double topScore, String preview) {
        TraceContext ctx = getCurrentContext();
        if (ctx == null) return;

        TraceStep step = ctx.newStep("RETRIEVAL", "RAG 检索");
        step.setInput(TraceStep.truncate(query, 500));
        step.setOutput(String.format("召回 %d 个片段，最高分 %.2f", retrievedCount, topScore));
        step.finish();
        ctx.addStep(step);
    }

    /**
     * 记录 RAG 检索步骤（显式传入 ctx，用于 Reactor Context 路径）
     */
    public void recordRetrievalStep(TraceContext ctx, String query, int retrievedCount, double topScore, String preview) {
        if (ctx == null) return;

        TraceStep step = ctx.newStep("RETRIEVAL", "RAG 检索");
        step.setInput(TraceStep.truncate(query, 500));
        step.setOutput(String.format("召回 %d 个片段，最高分 %.2f", retrievedCount, topScore));
        step.finish();
        ctx.addStep(step);
    }

    /**
     * 记录汇总步骤（PlanAndExecute 的 Summarizer 阶段）
     *
     * @param ctx        Trace 上下文（从 ThreadLocal 取）
     * @param input      汇总输入（各子任务结果拼接）
     * @param output     汇总输出（最终回复）
     * @param durationMs 耗时
     */
    public void recordSummarizeStep(TraceContext ctx, String input, String output, long durationMs) {
        if (ctx == null) return;

        TraceStep step = ctx.newStep("SUMMARIZE", "结果汇总");
        step.setInput(TraceStep.truncate(input, 500));
        step.setOutput(TraceStep.truncate(output, 500));
        step.setDurationMs(durationMs);
        step.finish();
        ctx.addStep(step);
    }

    /**
     * 记录记忆注入步骤（手动埋点，从 ThreadLocal 取 ctx）
     */
    public void recordMemoryStep(int longTermCount, int shortTermCount) {
        TraceContext ctx = getCurrentContext();
        if (ctx == null) return;

        TraceStep step = ctx.newStep("MEMORY_INJECTION", "上下文构建");
        step.setOutput(String.format("长期记忆 %d 条 + 短期记忆 %d 条", longTermCount, shortTermCount));
        step.finish();
        ctx.addStep(step);
    }

    /**
     * 记录记忆注入步骤（显式传入 ctx，用于 Reactor Context 路径）
     */
    public void recordMemoryStep(TraceContext ctx, int longTermCount, int shortTermCount) {
        if (ctx == null) return;

        TraceStep step = ctx.newStep("MEMORY_INJECTION", "上下文构建");
        step.setOutput(String.format("长期记忆 %d 条 + 短期记忆 %d 条", longTermCount, shortTermCount));
        step.finish();
        ctx.addStep(step);
    }

    /**
     * 获取当前 TraceContext（ThreadLocal）
     */
    public TraceContext getCurrentContext() {
        return CURRENT.get();
    }

    /**
     * 设置当前 TraceContext（供 DefaultAgentExecutionEngine 同步路径使用）
     */
    public void setCurrentContext(TraceContext ctx) {
        CURRENT.set(ctx);
    }

    /**
     * 清理当前 TraceContext
     */
    public void clearCurrentContext() {
        CURRENT.remove();
    }
}
