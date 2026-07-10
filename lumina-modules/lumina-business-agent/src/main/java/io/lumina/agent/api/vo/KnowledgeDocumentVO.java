package io.lumina.agent.api.vo;

import io.lumina.agent.infrastructure.entity.KnowledgeDocumentDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识文档 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long documentId;
    private String documentUuid;
    private Long agentId;
    private Long kbId;
    private String title;
    private String format;
    private String language;
    private String embeddingModel;
    private Integer chunkCount;
    private String vectorDocIds;
    private Long fileSize;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static KnowledgeDocumentVO from(KnowledgeDocumentDO do_) {
        if (do_ == null) {
            return null;
        }
        return KnowledgeDocumentVO.builder()
                .documentId(do_.getDocumentId())
                .documentUuid(do_.getDocumentUuid())
                .agentId(do_.getAgentId())
                .kbId(do_.getKbId())
                .title(do_.getTitle())
                .format(do_.getFormat())
                .language(do_.getLanguage())
                .embeddingModel(do_.getEmbeddingModel())
                .chunkCount(do_.getChunkCount())
                .vectorDocIds(do_.getVectorDocIds())
                .fileSize(do_.getFileSize())
                .status(do_.getStatus())
                .createTime(do_.getCreateTime())
                .updateTime(do_.getUpdateTime())
                .build();
    }
}
