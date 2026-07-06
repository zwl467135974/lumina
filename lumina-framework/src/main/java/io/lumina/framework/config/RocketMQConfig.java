package io.lumina.framework.config;

/**
 * RocketMQ 常量定义
 *
 * <p>集中管理 Topic、Consumer Group、Tag 名称，避免硬编码。
 *
 * @author Lumina Team
 * @since 1.3.0
 */
public final class RocketMQConfig {

    private RocketMQConfig() {
    }

    /**
     * 知识库文档处理 Topic
     */
    public static final String TOPIC_KNOWLEDGE_INGEST = "lumina-knowledge-ingest";

    /**
     * 知识库文档处理消费者组
     */
    public static final String GROUP_KNOWLEDGE_INGEST = "lumina-knowledge-ingest-group";

    /**
     * 审计日志 Topic（预留，后续审计从 @Async 迁移到 MQ 时使用）
     */
    public static final String TOPIC_AUDIT_LOG = "lumina-audit-log";

    /**
     * 审计日志消费者组（预留）
     */
    public static final String GROUP_AUDIT_LOG = "lumina-audit-log-group";

    /**
     * Agent 异步任务 Topic
     */
    public static final String TOPIC_AGENT_TASK = "lumina-agent-task";

    /**
     * Agent 异步任务消费者组
     */
    public static final String GROUP_AGENT_TASK = "lumina-agent-task-group";
}
