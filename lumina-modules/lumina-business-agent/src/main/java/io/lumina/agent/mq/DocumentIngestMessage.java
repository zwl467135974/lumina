package io.lumina.agent.mq;

import java.io.Serializable;

/**
 * 知识库文档处理消息
 *
 * <p>由 KnowledgeServiceImpl 在上传时发送，DocumentIngestConsumer 异步消费。
 *
 * @author Lumina Team
 * @since 1.3.0
 */
public class DocumentIngestMessage implements Serializable {

    private static final long serialVersionUID = 2L;

    private String uuid;
    private String filePath;
    private String format;
    private Long agentId;
    private Long tenantId;
    /**
     * 所属知识库 ID（可空，向后兼容老数据）。
     *
     * <p>异步 ingest 链路下需要把 kbId 透传到 consumer，用于：
     * <ul>
     *   <li>写入 {@code lumina_knowledge_chunk.kb_id}（MySQL 关键词检索过滤）</li>
     *   <li>stamp 到 Qdrant payload 的 {@code kb_id} 字段（向量检索可按 kb 二次过滤）</li>
     * </ul>
     */
    private Long kbId;
    private int chunkSize;
    private int overlap;

    public DocumentIngestMessage() {
    }

    public DocumentIngestMessage(String uuid, String filePath, String format, Long agentId,
                                 Long tenantId, int chunkSize, int overlap) {
        this(uuid, filePath, format, agentId, tenantId, null, chunkSize, overlap);
    }

    public DocumentIngestMessage(String uuid, String filePath, String format, Long agentId,
                                 Long tenantId, Long kbId, int chunkSize, int overlap) {
        this.uuid = uuid;
        this.filePath = filePath;
        this.format = format;
        this.agentId = agentId;
        this.tenantId = tenantId;
        this.kbId = kbId;
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public int getOverlap() { return overlap; }
    public void setOverlap(int overlap) { this.overlap = overlap; }

    @Override
    public String toString() {
        return "DocumentIngestMessage{uuid='" + uuid + "', format='" + format + "', agentId=" + agentId
                + ", kbId=" + kbId + "}";
    }
}
