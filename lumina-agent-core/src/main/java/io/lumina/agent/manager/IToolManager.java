package io.lumina.agent.manager;

import io.lumina.agent.tool.ToolDefinition;

import java.util.List;
import java.util.Set;

/**
 * 工具管理器统一接口
 *
 * <p>定义工具管理器的标准接口。
 * <p>目前由 {@link EnhancedToolManager} 实现此接口。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public interface IToolManager {

    /**
     * 注册工具
     *
     * @param toolName 工具名称
     * @param tool     工具实例或定义
     */
    void registerTool(String toolName, Object tool);

    /**
     * 获取工具
     *
     * @param toolName 工具名称
     * @return 工具实例或定义
     */
    Object getTool(String toolName);

    /**
     * 检查工具是否存在
     *
     * @param toolName 工具名称
     * @return 是否存在
     */
    boolean hasTool(String toolName);

    /**
     * 获取所有工具名称
     *
     * @return 工具名称集合
     */
    Set<String> getAllToolNames();

    /**
     * 移除工具
     *
     * @param toolName 工具名称
     */
    void removeTool(String toolName);

    /**
     * 获取所有工具定义（如果支持）
     *
     * @return 工具定义列表，如果不支持则返回空列表
     */
    default List<ToolDefinition> getAllTools() {
        return List.of();
    }
}

