package io.lumina.agent.api.dto.a2a;

import lombok.Data;

import java.util.List;

/**
 * A2A Agent Card（Agent2Agent 开放协议的 Agent 发现描述）
 *
 * <p>对齐 A2A 协议卡片字段子集；{@code url} 指向本服务的 JSON-RPC 端点。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
public class A2aAgentCard {

    /** Agent 展示名 */
    private String name;

    /** 能力描述（供调用方 Agent 判断是否委派任务） */
    private String description;

    /** JSON-RPC 端点地址（调用方 message/send 的目标） */
    private String url;

    /** Lumina 内部 Agent ID（调试用，可选） */
    private Long agentId;

    private String version;

    private String protocolVersion;

    private String preferredTransport;

    private Capabilities capabilities;

    /** 默认输入/输出模态（当前仅文本） */
    private List<String> defaultInputModes;

    private List<String> defaultOutputModes;

    /** Agent 技能声明（映射 Lumina Agent 的能力标签） */
    private List<Skill> skills;

    @Data
    public static class Capabilities {
        private Boolean streaming = false;
        private Boolean pushNotifications = false;
        private Boolean stateTransitionHistory = false;
    }

    @Data
    public static class Skill {
        private String id;
        private String name;
        private String description;
        private List<String> tags;
    }
}
