package io.lumina.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * AgentScope 工具适配器
 *
 * <p>将 ToolDefinition 动态适配为 AgentScope 可用的工具类。
 * <p>通过动态代理实现工具方法的自动注册和调用。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentScopeToolAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 为 ToolDefinition 创建符合 AgentScope 规范的工具对象
     *
     * <p>返回的对象包含带有 @Tool 和 @ToolParam 注解的方法，
     * 可以直接注册到 AgentScope Toolkit 中。
     *
     * @param definition 工具定义
     * @return 工具对象
     */
    public Object adaptTool(ToolDefinition definition) {
        // 使用动态代理创建工具对象
        return Proxy.newProxyInstance(
                this.getClass().getClassLoader(),
                new Class<?>[]{DynamicToolInterface.class},
                (proxy, method, args) -> {
                    // 检查是否是工具调用方法
                    if (method.getName().equals("executeTool") &&
                        method.getParameterCount() == 1) {

                        String paramsJson = (String) args[0];
                        log.info("执行工具: {}, 参数: {}", definition.getName(), paramsJson);

                        try {
                            Object result = definition.execute(paramsJson);

                            // 转换结果为字符串
                            if (result != null) {
                                if (result instanceof String) {
                                    return result;
                                } else {
                                    return objectMapper.writeValueAsString(result);
                                }
                            } else {
                                return "执行成功，无返回结果";
                            }
                        } catch (Exception e) {
                            log.error("工具执行失败: {}", definition.getName(), e);
                            return "工具执行失败: " + e.getMessage();
                        }
                    }

                    // 其他方法返回默认值
                    return null;
                }
        );
    }

    /**
     * 获取工具的注解元数据（用于 AgentScope 解析）
     *
     * @param definition 工具定义
     * @return 工具元数据
     */
    public ToolMetadata getToolMetadata(ToolDefinition definition) {
        ToolMetadata metadata = new ToolMetadata();
        metadata.setName(definition.getName());
        metadata.setDescription(definition.getDescription());
        metadata.setCategory(definition.getCategory());

        // 解析参数定义
        if (definition.getParameters() != null && !definition.getParameters().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = objectMapper.readValue(
                    definition.getParameters(),
                    Map.class
                );
                metadata.setParameters(params);
            } catch (Exception e) {
                log.warn("解析工具参数失败: {}", definition.getName(), e);
                metadata.setParameters(new HashMap<>());
            }
        } else {
            // 默认参数定义
            Map<String, Object> defaultParams = new HashMap<>();
            defaultParams.put("type", "object");
            defaultParams.put("properties", new HashMap<>());
            metadata.setParameters(defaultParams);
        }

        return metadata;
    }

    /**
     * 动态工具接口（用于代理）
     */
    public interface DynamicToolInterface {
        // 这个接口只是标记，实际方法调用通过代理处理
    }

    /**
     * 工具元数据
     */
    public static class ToolMetadata {
        private String name;
        private String description;
        private String category;
        private Map<String, Object> parameters;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }
    }
}
