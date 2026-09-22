package io.lumina.agent.tool.spill;

import io.lumina.agent.config.LuminaAgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 历史消息外存化器（v3.13 批次 2.3，输入侧外存 / 文件状态水合的 Lumina 化）
 *
 * <p>超过阈值的历史 user/assistant 消息全文存入 {@link ToolArtifactStore}
 * （复用 A5 的存档表与租户隔离），记忆里只留 head/tail 预览 + artifactId
 * 标记——长会话每轮回放不再为同一条大消息（粘贴的代码块、长文档、大结果）
 * 反复支付 token，模型需要时可经 {@code util.getArtifact} 按需水合取回全文。
 *
 * <p>与 {@link ToolResultSpiller} 的语义差异（独立成类的原因）：
 * <ul>
 *   <li><b>降级路径不同</b>：工具结果存档失败可硬截断（best-effort），
 *       历史消息<b>宁可贵不能失真</b>——存档失败降级为原文原样入记忆</li>
 *   <li><b>独立阈值</b>：历史回放成本与工具结果成本量级不同，
 *       独立配置（history-threshold-chars 等）</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 3.13.0
 */
@Slf4j
@Component
public class HistorySpiller {

    private final LuminaAgentProperties agentProperties;

    @Nullable
    private final ToolArtifactStore artifactStore;

    public HistorySpiller(LuminaAgentProperties agentProperties,
                          ObjectProvider<ToolArtifactStore> artifactStore) {
        this.agentProperties = agentProperties;
        this.artifactStore = artifactStore.getIfAvailable();
    }

    /**
     * 按需外存化历史消息（未超阈值/未启用/无会话上下文原样返回）
     *
     * @param role           消息角色（user/assistant，仅用于存档来源标记与日志）
     * @param conversationId 会话 ID（存档按会话关联；null 直接原样返回）
     * @param content        消息全文
     * @return 记忆可见内容（预览 + 取回标记，或原文）
     */
    public String spillIfNeeded(String role, String conversationId, String content) {
        LuminaAgentProperties.SpillConfig config = agentProperties.getTool().getSpill();
        if (content == null || conversationId == null || conversationId.isBlank()
                || !config.isEnabled() || !config.isHistoryEnabled()
                || content.length() <= config.getHistoryThresholdChars()) {
            return content;
        }
        if (artifactStore == null) {
            // 无存档实现（business 侧未装配）：保持原文，绝不因缺组件截断历史
            return content;
        }
        try {
            String artifactId = artifactStore.save(conversationId, "history." + role, content);
            String preview = buildPreview(content, config, artifactId);
            log.info("历史消息已外存化: role={}, 原文 {} 字符 -> 预览 {} 字符, artifactId={}",
                    role, content.length(), preview.length(), artifactId);
            return preview;
        } catch (Exception e) {
            // 历史语义：存档失败降级为原文（宁可贵，不失真）
            log.warn("历史消息存档失败，保留原文入记忆: role={}, length={}, error={}",
                    role, content.length(), e.getMessage());
            return content;
        }
    }

    private String buildPreview(String content, LuminaAgentProperties.SpillConfig config, String artifactId) {
        int head = Math.min(config.getHistoryHeadChars(), content.length());
        int tail = Math.min(config.getHistoryTailChars(), Math.max(0, content.length() - head));
        int omitted = content.length() - head - tail;
        return content.substring(0, head)
                + "\n\n[...历史消息过长，已省略 " + omitted + " 字符。全文已存档 artifactId="
                + artifactId + "，如需查看请调用 util.getArtifact 工具取回...]\n\n"
                + content.substring(content.length() - tail);
    }
}
