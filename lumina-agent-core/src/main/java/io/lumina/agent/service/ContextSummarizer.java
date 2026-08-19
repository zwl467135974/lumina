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

    /**
     * 检查点式压缩摘要（两级压缩管线的第二级）
     *
     * <p>与 {@link #summarize} 的区别：
     * <ul>
     *   <li>KV 前缀对齐：摘要调用复用会话自己的系统提示词 + 被压缩消息重放，
     *       压缩指令作为最后一条 user 消息——命中 provider 前缀缓存，成本接近增量</li>
     *   <li>结构化检查点：8 段固定格式（意图/概念/文件/错误/待办/当前工作/下一步/关键上下文），
     *       关键路径、命令、错误串逐字保留</li>
     *   <li>收缩保证：摘要估算 token 必须小于被压缩区，否则截断或放弃（返回 null）</li>
     *   <li>旧摘要合并：存在先前检查点时合并不前拷，防多级压缩膨胀</li>
     * </ul>
     *
     * @param olderMessages           需要压缩的旧消息列表（按时间顺序）
     * @param conversationSystemPrompt 会话的系统提示词（用于前缀对齐，null 用默认）
     * @param previousSummary         先前的检查点摘要（null 表示首次压缩）
     * @return 检查点摘要文本；压缩无收益（区域太小）或失败时返回 null（调用方降级为修剪）
     * @since 3.11.0
     */
    String summarizeCheckpoint(List<MemoryManager.Memory> olderMessages,
                               String conversationSystemPrompt, String previousSummary);
}
