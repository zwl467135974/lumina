package io.lumina.agent.api.dto.openai;

import lombok.Data;

import java.util.List;

/**
 * OpenAI Models 列表响应（Agent 列表伪装成 model）
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
public class ModelListResponse {

    /**
     * 对象类型（固定 list）
     */
    private String object = "list";

    /**
     * 模型列表
     */
    private List<ModelInfo> data;

    /**
     * 模型单元
     */
    @Data
    public static class ModelInfo {

        /**
         * 模型标识（agent-{id}）
         */
        private String id;

        /**
         * 对象类型（固定 model）
         */
        private String object = "model";

        /**
         * 创建时间（Unix 秒）
         */
        private Long created;

        /**
         * 归属方
         */
        @com.fasterxml.jackson.annotation.JsonProperty("owned_by")
        private String ownedBy = "lumina";
    }
}
