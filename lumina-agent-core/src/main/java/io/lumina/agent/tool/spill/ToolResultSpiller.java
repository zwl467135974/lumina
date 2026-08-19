package io.lumina.agent.tool.spill;

import io.lumina.agent.config.LuminaAgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 工具结果外存化器（spill）
 *
 * <p>超过阈值的工具结果全文存入 {@link ToolArtifactStore}，
 * 模型侧替换为 head/tail 预览 + 取回提示（提示字节数在阈值内预留，
 * 保证替换后永不超过阈值）。存档失败降级为硬截断（best-effort，
 * 绝不把成功调用变失败）。
 *
 * <p>防回环：取回工具（util.getArtifact）的结果不再 spill，
 * 超长时直接硬截断——避免"取回 → 再存档 → 再取回"死循环。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@Component
public class ToolResultSpiller {

    /** 取回工具名（其结果不再 spill，防回环） */
    public static final String RETRIEVAL_TOOL_NAME = "util.getArtifact";

    private final LuminaAgentProperties agentProperties;

    @Nullable
    private final ToolArtifactStore artifactStore;

    public ToolResultSpiller(LuminaAgentProperties agentProperties,
                             ObjectProvider<ToolArtifactStore> artifactStore) {
        this.agentProperties = agentProperties;
        this.artifactStore = artifactStore.getIfAvailable();
    }

    /**
     * 按需外存化（未超阈值/未启用原样返回）
     *
     * @return 模型可见的结果（有界）
     */
    public String spillIfNeeded(String toolName, String conversationId, String result) {
        LuminaAgentProperties.SpillConfig config = agentProperties.getTool().getSpill();
        if (result == null || !config.isEnabled() || result.length() <= config.getThresholdChars()) {
            return result;
        }
        // 取回工具的结果不再 spill，硬截断防回环
        if (RETRIEVAL_TOOL_NAME.equals(toolName)) {
            return hardTruncate(result, config);
        }
        try {
            String artifactId = artifactStore.save(conversationId, toolName, result);
            String preview = buildPreview(result, config, artifactId);
            // 预留校验：预览含提示行也必须在阈值内
            if (preview.length() > config.getThresholdChars()) {
                log.warn("spill 预览超出阈值（{} > {}），降级硬截断: tool={}",
                        preview.length(), config.getThresholdChars(), toolName);
                return hardTruncate(result, config);
            }
            log.info("工具结果已外存化: tool={}, 原文 {} 字符 -> 预览 {} 字符, artifactId={}",
                    toolName, result.length(), preview.length(), artifactId);
            return preview;
        } catch (Exception e) {
            log.warn("工具结果存档失败，降级硬截断: tool={}, error={}", toolName, e.getMessage());
            return hardTruncate(result, config);
        }
    }

    private String buildPreview(String result, LuminaAgentProperties.SpillConfig config, String artifactId) {
        int head = Math.min(config.getHeadChars(), result.length());
        int tail = Math.min(config.getTailChars(), Math.max(0, result.length() - head));
        int omitted = result.length() - head - tail;
        return result.substring(0, head)
                + "\n\n[...工具结果过长，已省略 " + omitted + " 字符。完整结果已存档 artifactId="
                + artifactId + "，如需全文请调用 util.getArtifact 工具取回...]\n\n"
                + result.substring(result.length() - tail);
    }

    private String hardTruncate(String result, LuminaAgentProperties.SpillConfig config) {
        int head = Math.min(config.getHeadChars(), result.length());
        return result.substring(0, head)
                + "\n\n[...工具结果过长，已截断 " + (result.length() - head) + " 字符...]";
    }
}
