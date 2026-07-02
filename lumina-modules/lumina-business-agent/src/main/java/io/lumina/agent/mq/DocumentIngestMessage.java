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

    private static final long serialVersionUID = 1L;

    private String uuid;
    private String filePath;
    private String format;
    private Long agentId;
    private Long tenantId;
    private int chunkSize;
    private int overlap;

    public DocumentIngestMessage() {
    }

    public DocumentIngestMessage(String uuid, String filePath, String format, Long agentId,
                                 Long tenantId, int chunkSize, int overlap) {
        this.uuid = uuid;
        this.filePath = filePath;
        this.format = format;
        this.agentId = agentId;
        this.tenantId = tenantId;
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
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public int getOverlap() { return overlap; }
    public void setOverlap(int overlap) { this.overlap = overlap; }

    @Override
    public String toString() {
        return "DocumentIngestMessage{uuid='" + uuid + "', format='" + format + "', agentId=" + agentId + "}";
    }
}
