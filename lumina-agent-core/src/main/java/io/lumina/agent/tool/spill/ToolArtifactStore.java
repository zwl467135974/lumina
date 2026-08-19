package io.lumina.agent.tool.spill;

/**
 * 工具结果存档存储（超大工具输出的外存化）
 *
 * <p>借鉴 DeepSeek Harness 的 spill 设计：单条超大工具结果全文存档，
 * 模型侧只保留 head/tail 预览 + 存档 ID，模型可按需取回全文——
 * 上下文和日志同时有界，信息不丢失。
 *
 * <p>实现注意：存储必须按租户隔离（get 时校验 tenant_id），
 * 只管理存储不管理保留策略。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
public interface ToolArtifactStore {

    /**
     * 存档一条工具结果全文
     *
     * @param conversationId 会话 ID（可空）
     * @param toolName       工具名
     * @param content        全文
     * @return 存档 ID（供 util.getArtifact 取回）
     */
    String save(String conversationId, String toolName, String content);

    /**
     * 取回存档全文（租户隔离）
     *
     * @return 全文；不存在/跨租户返回 null
     */
    String get(String artifactId);
}
