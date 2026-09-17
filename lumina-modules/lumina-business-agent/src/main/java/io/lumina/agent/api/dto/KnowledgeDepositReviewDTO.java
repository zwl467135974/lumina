package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识沉淀审核 DTO
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
public class KnowledgeDepositReviewDTO {

    /** true=通过并入库 false=驳回 */
    @NotNull(message = "审核结论不能为空")
    private Boolean approved;

    /** 审核意见（驳回原因建议填写） */
    @Size(max = 500, message = "审核意见最长 500 字符")
    private String comment;
}
