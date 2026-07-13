package io.lumina.notification.domain.enums;

import lombok.Getter;

/**
 * 通知严重程度枚举
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Getter
public enum NotificationSeverity {

    /**
     * 提示
     */
    INFO("提示"),

    /**
     * 警告
     */
    WARN("警告"),

    /**
     * 错误
     */
    ERROR("错误");

    /**
     * 中文展示名称
     */
    private final String displayName;

    NotificationSeverity(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 根据名称获取枚举
     */
    public static NotificationSeverity fromName(String name) {
        for (NotificationSeverity severity : values()) {
            if (severity.name().equalsIgnoreCase(name)) {
                return severity;
            }
        }
        throw new IllegalArgumentException("未知的通知严重程度: " + name);
    }
}
