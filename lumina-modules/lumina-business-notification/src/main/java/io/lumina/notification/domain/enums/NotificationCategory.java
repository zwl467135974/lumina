package io.lumina.notification.domain.enums;

import lombok.Getter;

/**
 * 通知分类枚举
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Getter
public enum NotificationCategory {

    /**
     * 预算告警
     */
    BUDGET("预算告警"),

    /**
     * 任务通知
     */
    TASK("任务通知"),

    /**
     * 工作流
     */
    WORKFLOW("工作流"),

    /**
     * 文档处理
     */
    DOCUMENT("文档处理"),

    /**
     * 评估
     */
    EVALUATION("评估"),

    /**
     * 定时触发器
     */
    TRIGGER("定时触发器"),

    /**
     * 系统
     */
    SYSTEM("系统");

    /**
     * 中文展示名称
     */
    private final String displayName;

    NotificationCategory(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 根据名称获取枚举
     */
    public static NotificationCategory fromName(String name) {
        for (NotificationCategory category : values()) {
            if (category.name().equalsIgnoreCase(name)) {
                return category;
            }
        }
        throw new IllegalArgumentException("未知的通知分类: " + name);
    }
}
