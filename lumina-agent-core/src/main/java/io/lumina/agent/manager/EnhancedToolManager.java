package io.lumina.agent.manager;

import io.lumina.agent.tool.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增强的工具管理器
 *
 * <p>支持工具自动发现、注册、调用和描述。
 * <p>这是推荐使用的工具管理器，提供完整的工具管理功能。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class EnhancedToolManager implements IToolManager {

    /**
     * 工具定义注册表
     */
    private final Map<String, ToolDefinition> toolDefinitions = new ConcurrentHashMap<>();

    /**
     * 工具分类索引
     */
    private final Map<String, Set<String>> categoryIndex = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 初始化时自动扫描工具
     */
    @PostConstruct
    public void scanAndRegisterTools() {
        log.info("开始扫描 Agent 工具...");

        // 扫描所有带有 @RestController 和 @Controller 的类
        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(RestController.class);
        controllers.putAll(applicationContext.getBeansWithAnnotation(Controller.class));

        int toolCount = 0;
        for (Object controller : controllers.values()) {
            Method[] methods = controller.getClass().getDeclaredMethods();
            for (Method method : methods) {
                // 检查是否有 @AgentTool 注解
                if (method.isAnnotationPresent(io.lumina.agent.tool.AgentTool.class)) {
                    io.lumina.agent.tool.AgentTool annotation = method.getAnnotation(io.lumina.agent.tool.AgentTool.class);

                    // 创建工具定义
                    String toolName = annotation.name().isEmpty() ?
                            method.getDeclaringClass().getSimpleName() + "." + method.getName() :
                            annotation.name();

                    ToolDefinition definition = ToolDefinition.create(
                            toolName,
                            annotation.description(),
                            annotation.category(),
                            params -> {
                                // 调用原始方法
                                method.setAccessible(true);
                                return method.invoke(controller, parseParameters(params, method));
                            }
                    );

                    registerToolDefinition(definition);
                    toolCount++;
                }
            }
        }

        log.info("工具扫描完成，共注册 {} 个工具", toolCount);
    }

    /**
     * 注册工具定义
     */
    public void registerToolDefinition(ToolDefinition definition) {
        toolDefinitions.put(definition.getName(), definition);

        // 更新分类索引
        String category = definition.getCategory();
        if (category != null && !category.isEmpty()) {
            categoryIndex.computeIfAbsent(category, k -> new HashSet<>()).add(definition.getName());
        }

        log.info("注册工具定义: {} (分类: {})", definition.getName(), definition.getCategory());
    }

    @Override
    public void registerTool(String toolName, Object tool) {
        if (tool instanceof ToolDefinition) {
            registerToolDefinition((ToolDefinition) tool);
        } else {
            // 如果不是 ToolDefinition，创建一个简单的包装
            ToolDefinition definition = ToolDefinition.create(
                    toolName,
                    "工具: " + toolName,
                    "default",
                    params -> tool
            );
            registerToolDefinition(definition);
        }
    }

    /**
     * 注册工具（简化版本）
     */
    public void registerTool(String name, String description, String category, ToolDefinition.ToolExecutor executor) {
        ToolDefinition definition = ToolDefinition.create(name, description, category, executor);
        registerToolDefinition(definition);
    }

    /**
     * 注册工具（无分类）
     */
    public void registerTool(String name, String description, ToolDefinition.ToolExecutor executor) {
        registerTool(name, description, "default", executor);
    }

    @Override
    public Object getTool(String toolName) {
        return getToolDefinition(toolName);
    }

    /**
     * 获取工具定义
     */
    public ToolDefinition getToolDefinition(String toolName) {
        return toolDefinitions.get(toolName);
    }

    @Override
    public boolean hasTool(String toolName) {
        return toolDefinitions.containsKey(toolName);
    }

    @Override
    public Set<String> getAllToolNames() {
        return toolDefinitions.keySet();
    }

    @Override
    public List<ToolDefinition> getAllTools() {
        return new ArrayList<>(toolDefinitions.values());
    }

    /**
     * 根据分类获取工具
     */
    public Set<String> getToolsByCategory(String category) {
        return categoryIndex.getOrDefault(category, Collections.emptySet());
    }

    /**
     * 获取所有分类
     */
    public Set<String> getAllCategories() {
        return categoryIndex.keySet();
    }

    /**
     * 执行工具
     */
    public Object executeTool(String toolName, String params) throws Exception {
        ToolDefinition definition = getToolDefinition(toolName);
        if (definition == null) {
            throw new IllegalArgumentException("工具不存在: " + toolName);
        }

        log.info("执行工具: {}, 参数: {}", toolName, params);
        Object result = definition.execute(params);
        log.info("工具执行完成: {}", toolName);

        return result;
    }

    @Override
    public void removeTool(String toolName) {
        ToolDefinition definition = toolDefinitions.remove(toolName);
        if (definition != null && definition.getCategory() != null) {
            Set<String> tools = categoryIndex.get(definition.getCategory());
            if (tools != null) {
                tools.remove(toolName);
                if (tools.isEmpty()) {
                    categoryIndex.remove(definition.getCategory());
                }
            }
        }
        log.info("移除工具: {}", toolName);
    }

    /**
     * 获取工具描述信息（用于 AgentScope）
     */
    public Map<String, Object> getToolDescription(String toolName) {
        ToolDefinition definition = getToolDefinition(toolName);
        if (definition == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> description = new HashMap<>();
        description.put("name", definition.getName());
        description.put("description", definition.getDescription());
        description.put("category", definition.getCategory());
        description.put("parameters", definition.getParameters());
        description.put("enabled", definition.isEnabled());

        return description;
    }

    /**
     * 获取所有工具描述
     */
    public List<Map<String, Object>> getAllToolDescriptions() {
        List<Map<String, Object>> descriptions = new ArrayList<>();
        for (String toolName : getAllToolNames()) {
            descriptions.add(getToolDescription(toolName));
        }
        return descriptions;
    }

    /**
     * 解析参数（简单实现，TODO: 支持 JSON Schema）
     */
    private Object[] parseParameters(String params, Method method) {
        // TODO: 根据 JSON Schema 解析参数
        // 目前返回空数组，适用于无参数方法
        return new Object[0];
    }
}
