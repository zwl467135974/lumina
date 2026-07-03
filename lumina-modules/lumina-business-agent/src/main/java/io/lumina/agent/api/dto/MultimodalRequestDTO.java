package io.lumina.agent.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 多模态执行请求 DTO
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Data
public class MultimodalRequestDTO {

    /** 任务描述 */
    private String task;

    /** 图片文件 UUID 列表 */
    private List<String> fileUuids;

    /** 会话 UUID（可选） */
    private String conversationId;
}
