package io.lumina.agent.tracing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 推理链单步记录
 *
 * <p>表示 Agent 执行过程中的一个步骤（推理/工具调用/RAG检索/记忆注入）。
 *
 * @author Lumina Team
 * @since 3.7.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraceStep {

    /** 步骤序号（从 1 开始） */
    private int seq;

    /** 步骤类型：REASONING / TOOL_CALL / RETRIEVAL / MEMORY_INJECTION / SUMMARIZE */
    private String type;

    /** 步骤名称（如"推理 第1轮"/"工具: webSearch"） */
    private String name;

    /** 输入内容（截断到 500 字符） */
    private String input;

    /** 输出内容（截断到 500 字符） */
    private String output;

    /** 输入 Token（仅 REASONING 步骤有值） */
    private Integer promptTokens;

    /** 输出 Token（仅 REASONING 步骤有值） */
    private Integer completionTokens;

    /** 耗时（毫秒） */
    private long durationMs;

    /** 步骤开始时间戳（内部用，不序列化） */
    private transient long startTimestamp;

    /**
     * 创建并启动计时
     */
    public static TraceStep start(int seq, String type, String name) {
        TraceStep step = new TraceStep();
        step.setSeq(seq);
        step.setType(type);
        step.setName(name);
        step.startTimestamp = System.currentTimeMillis();
        return step;
    }

    /**
     * 完成计时
     */
    public void finish() {
        this.durationMs = System.currentTimeMillis() - startTimestamp;
    }

    /**
     * 截断文本到指定长度
     */
    public static String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
