package io.lumina.agent.service;

import io.lumina.agent.manager.MemoryManager;

import java.util.List;

/**
 * 上下文压缩服务（Context Summarizer）
 *
 * <p>当对话历史超过阈值时，将较早的消息用 LLM 摘要替代，
 * 避免直接丢弃导致上下文信息丢失。
 *
 * <p>工作流程：
 * <ol>
 *   <li>引擎层检测到 history.size() > threshold</li>
 *   <li>将 [0, size - recentKeepCount) 的旧消息交给 summarizer</li>
 *   <li>summarizer 调用 LLM 生成摘要文本</li>
 *   <li>引擎层将摘要作为 SYSTEM 消息注入，只保留最近 recentKeepCount 条原始消息</li>
 * </ol>
 *
 * @author Lumina Team
 * @since 3.8.0
 */
public interface ContextSummarizer {

    /**
     * 对旧消息列表生成摘要
     *
     * @param olderMessages 需要压缩的旧消息列表（按时间顺序）
     * @param agentName     Agent 名称（用于摘要的上下文）
     * @return 摘要文本（如"用户之前问了天气和股票价格，Agent 回答了..."）
     */
    String summarize(List<MemoryManager.Memory> olderMessages, String agentName);
}
