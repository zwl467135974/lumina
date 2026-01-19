package io.lumina.agent.tool;

import lombok.Data;

import java.io.Serializable;

/**
 * 工具定义
 *
 * <p>封装工具的元数据和执行逻辑。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class ToolDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 工具分类
     */
    private String category;

    /**
     * 工具参数定义（JSON Schema 格式）
     */
    private String parameters;

    /**
     * 执行器
     */
    private ToolExecutor executor;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 工具执行器接口
     */
    @FunctionalInterface
    public interface ToolExecutor extends Serializable {
        Object execute(String params) throws Exception;
    }

    /**
     * 创建工具定义
     */
    public static ToolDefinition create(String name, String description, ToolExecutor executor) {
        ToolDefinition definition = new ToolDefinition();
        definition.setName(name);
        definition.setDescription(description);
        definition.setExecutor(executor);
        definition.setEnabled(true);
        return definition;
    }

    /**
     * 创建工具定义（带分类）
     */
    public static ToolDefinition create(String name, String description, String category, ToolExecutor executor) {
        ToolDefinition definition = create(name, description, executor);
        definition.setCategory(category);
        return definition;
    }

    /**
     * 执行工具
     */
    public Object execute(String params) throws Exception {
        if (!enabled) {
            throw new IllegalStateException("工具未启用: " + name);
        }
        if (executor == null) {
            throw new IllegalStateException("工具执行器未配置: " + name);
        }
        return executor.execute(params);
    }
}
