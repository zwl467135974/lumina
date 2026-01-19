package io.lumina.agent.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具管理器
 *
 * <p>管理 Agent 可用的工具集合。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ToolManager {

    /**
     * 工具注册表
     */
    private final Map<String, Object> toolRegistry = new ConcurrentHashMap<>();

    /**
     * 注册工具
     *
     * @param toolName 工具名称
     * @param tool     工具实例
     */
    public void registerTool(String toolName, Object tool) {
        toolRegistry.put(toolName, tool);
        log.info("注册工具成功: {}", toolName);
    }

    /**
     * 获取工具
     *
     * @param toolName 工具名称
     * @return 工具实例
     */
    public Object getTool(String toolName) {
        return toolRegistry.get(toolName);
    }

    /**
     * 检查工具是否存在
     *
     * @param toolName 工具名称
     * @return 是否存在
     */
    public boolean hasTool(String toolName) {
        return toolRegistry.containsKey(toolName);
    }

    /**
     * 获取所有工具名称
     *
     * @return 工具名称列表
     */
    public java.util.Set<String> getAllToolNames() {
        return toolRegistry.keySet();
    }

    /**
     * 移除工具
     *
     * @param toolName 工具名称
     */
    public void removeTool(String toolName) {
        toolRegistry.remove(toolName);
        log.info("移除工具: {}", toolName);
    }
}
