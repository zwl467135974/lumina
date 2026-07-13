package io.lumina.agent.manager;

import io.lumina.agent.tool.ToolDefinition;
import io.lumina.agent.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

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
@RequiredArgsConstructor
public class EnhancedToolManager implements IToolManager {

    /**
     * 工具定义注册表
     */
    private final Map<String, ToolDefinition> toolDefinitions = new ConcurrentHashMap<>();

    /**
     * 工具分类索引
     */
    private final Map<String, Set<String>> categoryIndex = new ConcurrentHashMap<>();

    private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = JsonUtils.OBJECT_MAPPER;

    private final ApplicationContext applicationContext;

    /**
     * 应用就绪后自动扫描工具
     *
     * <p>使用 {@link EventListener}({@link ApplicationReadyEvent}) 而非 {@code @PostConstruct}，
     * 避免在 bean 初始化阶段通过 {@code applicationContext.getBeansWithAnnotation} 急切触发
     * Controller bean 创建，从而引入与 {@code AgentController → AgentServiceImpl →
     * DefaultAgentExecutionEngine → EnhancedToolManager} 的循环依赖。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void scanAndRegisterTools() {
        log.info("开始扫描 Agent 工具...");

        // 扫描所有可能包含 @AgentTool 的 Spring Bean
        // ApplicationReadyEvent 阶段所有 Bean 已就绪，无循环依赖风险
        Map<String, Object> candidates = applicationContext.getBeansWithAnnotation(RestController.class);
        candidates.putAll(applicationContext.getBeansWithAnnotation(Controller.class));
        candidates.putAll(applicationContext.getBeansWithAnnotation(Component.class));
        candidates.putAll(applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Service.class));

        int toolCount = 0;
        for (Object bean : candidates.values()) {
            // 跳过自身，避免扫描 EnhancedToolManager 的方法
            if (bean == this) {
                continue;
            }
            Method[] methods = bean.getClass().getDeclaredMethods();
            for (Method method : methods) {
                // 检查是否有 @AgentTool 注解
                if (method.isAnnotationPresent(io.lumina.agent.tool.AgentTool.class)) {
                    io.lumina.agent.tool.AgentTool annotation = method.getAnnotation(io.lumina.agent.tool.AgentTool.class);

                    // 跳过禁用的工具
                    if (!annotation.enabled()) {
                        continue;
                    }

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
                                return method.invoke(bean, parseParameters(params, method));
                            }
                    );

                    // 自动生成参数 JSON Schema（依赖 -parameters 编译选项获取真实参数名）
                    definition.setParameters(generateParametersSchema(method));

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
     * 根据方法签名自动生成参数 JSON Schema
     *
     * <p>依赖 Maven -parameters 编译选项获取真实参数名。
     * 将方法参数转换为 OpenAI/AgentScope 兼容的 JSON Schema 格式。
     *
     * @param method 工具方法
     * @return JSON Schema 字符串
     */
    private String generateParametersSchema(Method method) {
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (java.lang.reflect.Parameter param : parameters) {
            String paramName = param.getName();
            Class<?> paramType = param.getType();

            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", jsonTypeOf(paramType));
            prop.put("description", paramName);

            properties.put(paramName, prop);
            required.add(paramName);
        }

        schema.put("properties", properties);
        schema.put("required", required);

        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            log.warn("生成参数 Schema 失败: {}", method.getName(), e);
            return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
        }
    }

    /**
     * Java 类型 → JSON Schema type 映射
     */
    private String jsonTypeOf(Class<?> type) {
        if (type == String.class) return "string";
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) return "integer";
        if (type == Double.class || type == double.class || type == Float.class || type == float.class) return "number";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        if (java.util.Collection.class.isAssignableFrom(type)) return "array";
        if (Map.class.isAssignableFrom(type)) return "object";
        return "object";
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
            log.warn("工具参数解析失败: params={}, error={}", params, e.getMessage());
            throw new IllegalArgumentException("工具参数解析失败: " + e.getMessage(), e);
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
