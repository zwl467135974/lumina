package io.lumina.agent.domain.model;

import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.util.CryptoUtil;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * LLM Provider 领域实体
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class LlmProvider implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String provider;

    private String baseUrl;

    private String apiKeyEnc;

    private String defaultModel;

    private String defaultParams;

    private Integer status;

    private Long tenantId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 是否已配置 API Key
     */
    public boolean hasApiKey() {
        return StringUtils.hasText(apiKeyEnc);
    }

    /**
     * 解密获取 API Key
     */
    public String getDecryptedApiKey() {
        if (!hasApiKey()) {
            return null;
        }
        return CryptoUtil.decrypt(apiKeyEnc);
    }

    /**
     * 从明文设置 API Key（内部自动加密）
     */
    public void setApiKeyFromPlain(String plainKey) {
        if (StringUtils.hasText(plainKey)) {
            this.apiKeyEnc = CryptoUtil.encrypt(plainKey);
        }
    }

    /**
     * 获取脱敏后的 API Key
     */
    public String getMaskedApiKey() {
        if (!hasApiKey()) {
            return null;
        }
        try {
            return CryptoUtil.mask(CryptoUtil.decrypt(apiKeyEnc));
        } catch (Exception e) {
            return "****";
        }
    }

    /**
     * 启用
     */
    public void activate() {
        if (this.status != null && this.status == 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "LLM Provider 已是启用状态");
        }
        this.status = 1;
    }

    /**
     * 禁用
     */
    public void deactivate() {
        if (this.status != null && this.status == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "LLM Provider 已是禁用状态");
        }
        this.status = 0;
    }

    /**
     * 判断是否启用
     */
    public boolean isActive() {
        return this.status != null && this.status == 1;
    }

    /**
     * 验证配置名称
     */
    public void validateName() {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "配置名称不能为空");
        }
        if (name.length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "配置名称最多100个字符");
        }
    }

    /**
     * 验证供应商类型
     */
    public void validateProvider() {
        if (provider == null || provider.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "供应商不能为空");
        }
    }
}
