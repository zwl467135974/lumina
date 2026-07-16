package io.lumina.agent.api.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * LLM 供应商配置 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class LlmProviderVO {
    private Long id;
    private String name;
    private String provider;
    private String baseUrl;
    private String apiKeyMasked;
    private Boolean hasApiKey;
    private String defaultModel;
    private String defaultParams;
    private Integer status;
    private Integer priority;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
