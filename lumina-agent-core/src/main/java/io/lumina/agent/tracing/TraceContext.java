package io.lumina.agent.tracing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次 Agent 执行的 Trace 上下文
 *
 * <p>贯穿整个执行链路，收集所有步骤，最终由 TraceCollector 异步落库。
 *
 * @author Lumina Team
 * @since 3.7.0
 */
public class TraceContext {

    /** Reactor Context 中存储 TraceContext 的 key */
    public static final String KEY = "lumina.trace.context";

    private final String traceUuid;
    private String agentName;
    private String agentType;
    private Long agentId;
    private String conversationUuid;
    private String taskUuid;

    private String inputText;
    private String outputText;
    private String status = "RUNNING";

    private int totalPromptTokens;
    private int totalCompletionTokens;

    private final long startedAt;
    private long finishedAt;
    private long durationMs;

    private final List<TraceStep> steps = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger seqCounter = new AtomicInteger(0);

    public TraceContext() {
        this.traceUuid = UUID.randomUUID().toString();
        this.startedAt = System.currentTimeMillis();
    }

    /**
     * 创建新步骤并自动编号
     */
    public TraceStep newStep(String type, String name) {
        return TraceStep.start(seqCounter.incrementAndGet(), type, name);
    }

    /**
     * 添加已完成步骤
     */
    public void addStep(TraceStep step) {
        if (step != null) {
            steps.add(step);
            if (step.getPromptTokens() != null) {
                totalPromptTokens += step.getPromptTokens();
            }
            if (step.getCompletionTokens() != null) {
                totalCompletionTokens += step.getCompletionTokens();
            }
        }
    }

    /**
     * 标记执行成功
     */
    public void markSuccess(String output) {
        this.outputText = TraceStep.truncate(output, 2000);
        this.status = "SUCCESS";
        this.finishedAt = System.currentTimeMillis();
        this.durationMs = finishedAt - startedAt;
    }

    /**
     * 标记执行失败
     */
    public void markFailed(String error) {
        this.outputText = TraceStep.truncate(error, 2000);
        this.status = "FAILED";
        this.finishedAt = System.currentTimeMillis();
        this.durationMs = finishedAt - startedAt;
    }

    // === getter ===

    public String getTraceUuid() { return traceUuid; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getConversationUuid() { return conversationUuid; }
    public void setConversationUuid(String conversationUuid) { this.conversationUuid = conversationUuid; }
    public String getTaskUuid() { return taskUuid; }
    public void setTaskUuid(String taskUuid) { this.taskUuid = taskUuid; }
    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = TraceStep.truncate(inputText, 2000); }
    public String getOutputText() { return outputText; }
    public String getStatus() { return status; }
    public int getTotalPromptTokens() { return totalPromptTokens; }
    public int getTotalCompletionTokens() { return totalCompletionTokens; }
    public int getTotalTokens() { return totalPromptTokens + totalCompletionTokens; }
    public long getStartedAt() { return startedAt; }
    public long getFinishedAt() { return finishedAt; }
    public long getDurationMs() { return durationMs; }
    public List<TraceStep> getSteps() { return steps; }
}
