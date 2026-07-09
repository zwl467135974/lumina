package io.lumina.agent.api.vo;

import lombok.Data;

import java.time.LocalDateTime;

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
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
