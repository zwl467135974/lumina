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
     * 解析参数（支持 JSON Schema）
     *
     * @param params JSON 格式的参数字符串
     * @param method 目标方法
     * @return 解析后的参数数组
     */
    private Object[] parseParameters(String params, Method method) {
        if (params == null || params.trim().isEmpty() || "{}".equals(params.trim())) {
            return new Object[0];
        }

        try {
            // 使用 Jackson 解析 JSON
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> paramMap = objectMapper.readValue(params,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

            // 获取方法参数类型
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length == 0) {
                return new Object[0];
            }

            // 如果只有一个参数且类型是Map或Object，直接传递
            if (paramTypes.length == 1) {
                Class<?> paramType = paramTypes[0];
                if (Map.class.isAssignableFrom(paramType) || Object.class.equals(paramType)) {
                    return new Object[]{paramMap};
                }
                // 其他类型，尝试转换
                return new Object[]{convertParameter(paramMap, paramType, objectMapper)};
            }

            // 多个参数，按参数名匹配
            Object[] args = new Object[paramTypes.length];
            java.lang.reflect.Parameter[] parameters = method.getParameters();

            for (int i = 0; i < parameters.length; i++) {
                String paramName = parameters[i].getName();
                Class<?> paramType = paramTypes[i];

                if (paramMap.containsKey(paramName)) {
                    Object value = paramMap.get(paramName);
                    args[i] = convertParameter(value, paramType, objectMapper);
                } else {
                    // 参数不存在，使用默认值或null
                    args[i] = getDefaultValue(paramType);
                }
            }

            return args;
        } catch (Exception e) {
            log.error("解析参数失败: {}", params, e);
            return new Object[0];
        }
    }

    /**
     * 转换参数类型
     *
     * @param value 原始值
     * @param targetType 目标类型
     * @param ObjectMapper ObjectMapper实例
     * @return 转换后的值
     */
    private Object convertParameter(Object value, Class<?> targetType,
                                   com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        if (value == null) {
            return getDefaultValue(targetType);
        }

        // 如果类型已经匹配，直接返回
        if (targetType.isInstance(value)) {
            return value;
        }

        try {
            // 基本类型转换
            if (targetType == String.class) {
                return value.toString();
            } else if (targetType == Integer.class || targetType == int.class) {
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                return Integer.parseInt(value.toString());
            } else if (targetType == Long.class || targetType == long.class) {
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
                return Long.parseLong(value.toString());
            } else if (targetType == Double.class || targetType == double.class) {
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return Double.parseDouble(value.toString());
            } else if (targetType == Float.class || targetType == float.class) {
                if (value instanceof Number) {
                    return ((Number) value).floatValue();
                }
                return Float.parseFloat(value.toString());
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                if (value instanceof Boolean) {
                    return value;
                }
                return Boolean.parseBoolean(value.toString());
            } else if (Map.class.isAssignableFrom(targetType)) {
                return objectMapper.convertValue(value, Map.class);
            } else if (List.class.isAssignableFrom(targetType)) {
                return objectMapper.convertValue(value, List.class);
            } else {
                // 复杂对象，使用 Jackson 转换
                return objectMapper.convertValue(value, targetType);
            }
        } catch (Exception e) {
            log.warn("参数类型转换失败: {} -> {}", value.getClass(), targetType, e);
            return getDefaultValue(targetType);
        }
    }

    /**
     * 获取类型的默认值
     *
     * @param type 类型
     * @return 默认值
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        } else if (type == int.class) {
            return 0;
        } else if (type == long.class) {
            return 0L;
        } else if (type == float.class) {
            return 0.0f;
        } else if (type == double.class) {
            return 0.0d;
        } else if (type == char.class) {
            return '\0';
        } else if (type == short.class) {
            return (short) 0;
        } else if (type == byte.class) {
            return (byte) 0;
        }
        return null;
    }
}
