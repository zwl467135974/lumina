package io.lumina.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * ToolDefinition 到 AgentTool 的适配器
 *
 * <p>将 Lumina 的 ToolDefinition 适配为 AgentScope 的 AgentTool 接口实现。
 * 这样可以将 EnhancedToolManager 管理的工具动态注册到 AgentScope Toolkit。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
public class ToolDefinitionToAgentToolAdapter implements AgentTool {

    private final ToolDefinition toolDefinition;
    private final ObjectMapper objectMapper;
    private Map<String, Object> parametersSchema;

    public ToolDefinitionToAgentToolAdapter(ToolDefinition toolDefinition) {
        this.toolDefinition = toolDefinition;
        this.objectMapper = new ObjectMapper();
        this.parametersSchema = parseParametersSchema(toolDefinition);
    }

    @Override
    public String getName() {
        return toolDefinition.getName();
    }

    @Override
    public String getDescription() {
        return toolDefinition.getDescription() != null 
                ? toolDefinition.getDescription() 
                : "工具: " + toolDefinition.getName();
    }

    @Override
    public Map<String, Object> getParameters() {
        return parametersSchema;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> {
            try {
                // 从 ToolCallParam 中提取参数
                Map<String, Object> input = param.getInput();
                if (input == null && param.getToolUseBlock() != null) {
                    input = param.getToolUseBlock().getInput();
                }

                // 将参数转换为 JSON 字符串
                String paramsJson;
                if (input != null && !input.isEmpty()) {
                    paramsJson = objectMapper.writeValueAsString(input);
                } else {
                    paramsJson = "{}";
                }

                log.debug("执行工具: {}, 参数: {}", getName(), paramsJson);

                // 执行工具
                Object result = toolDefinition.execute(paramsJson);

                // 转换结果为字符串
                String resultString;
                if (result == null) {
                    resultString = "执行成功，无返回结果";
                } else if (result instanceof String) {
                    resultString = (String) result;
                } else {
                    resultString = objectMapper.writeValueAsString(result);
                }

                log.debug("工具执行完成: {}, 结果: {}", getName(), 
                        resultString.length() > 200 
                                ? resultString.substring(0, 200) + "..." 
                                : resultString);

                return ToolResultBlock.success(resultString);

            } catch (Exception e) {
                log.error("工具执行失败: {}", getName(), e);
                String errorMessage = e.getMessage() != null 
                        ? e.getMessage() 
                        : "工具执行失败: " + e.getClass().getSimpleName();
                return ToolResultBlock.error(errorMessage);
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * 解析参数定义（JSON Schema 格式）
     */
    private Map<String, Object> parseParametersSchema(ToolDefinition definition) {
        Map<String, Object> schema = new HashMap<>();

        if (definition.getParameters() != null && !definition.getParameters().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(
                        definition.getParameters(),
                        Map.class
                );
                schema.putAll(parsed);
            } catch (Exception e) {
                log.warn("解析工具参数 Schema 失败: {}, 使用默认 Schema", definition.getName(), e);
                schema = createDefaultSchema();
            }
        } else {
            // 如果没有定义参数，创建默认的空 Schema
            schema = createDefaultSchema();
        }

        // 确保包含必要的字段
        if (!schema.containsKey("type")) {
            schema.put("type", "object");
        }
        if (!schema.containsKey("properties")) {
            schema.put("properties", new HashMap<String, Object>());
        }
        if (!schema.containsKey("required")) {
            schema.put("required", new java.util.ArrayList<String>());
        }

        return schema;
    }

    /**
     * 创建默认的 JSON Schema
     */
    private Map<String, Object> createDefaultSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", new HashMap<String, Object>());
        schema.put("required", new java.util.ArrayList<String>());
        return schema;
    }
}

