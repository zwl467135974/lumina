package io.lumina.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.lumina.agent.monitor.ToolCircuitBreaker;
import io.lumina.agent.monitor.ToolInvocationRecord;
import io.lumina.agent.monitor.ToolInvocationRecorder;
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
    private final ToolInvocationRecorder recorder;
    private final ToolCircuitBreaker circuitBreaker;
    private Map<String, Object> parametersSchema;

    public ToolDefinitionToAgentToolAdapter(ToolDefinition toolDefinition) {
        this(toolDefinition, null, null);
    }

    public ToolDefinitionToAgentToolAdapter(ToolDefinition toolDefinition,
                                            ToolInvocationRecorder recorder,
                                            ToolCircuitBreaker circuitBreaker) {
        this.toolDefinition = toolDefinition;
        this.recorder = recorder;
        this.circuitBreaker = circuitBreaker;
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
            long start = System.currentTimeMillis();
            String toolName = getName();
            String paramsJson = "{}";
            try {
                // 熔断检查
                if (circuitBreaker != null && !circuitBreaker.allowExecution(toolName)) {
                    String msg = "工具熔断中，暂不可用: " + toolName;
                    doRecord(toolName, paramsJson, null, msg, System.currentTimeMillis() - start, false);
                    return ToolResultBlock.error(msg);
                }

                // 从 ToolCallParam 中提取参数
                Map<String, Object> input = param.getInput();
                if (input == null && param.getToolUseBlock() != null) {
                    input = param.getToolUseBlock().getInput();
                }

                // 将参数转换为 JSON 字符串
                if (input != null && !input.isEmpty()) {
                    paramsJson = objectMapper.writeValueAsString(input);
                }

                log.debug("执行工具: {}, 参数: {}", toolName, paramsJson);

                // 执行工具
                Object result = toolDefinition.execute(paramsJson);
                String resultString = toStringResult(result);

                long duration = System.currentTimeMillis() - start;
                log.debug("工具执行完成: {}, 耗时: {}ms", toolName, duration);

                // 记录成功 + 熔断反馈
                doRecord(toolName, paramsJson, resultString, null, duration, true);
                if (circuitBreaker != null) {
                    circuitBreaker.recordSuccess(toolName);
                }

                return ToolResultBlock.text(resultString);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - start;
                log.error("工具执行失败: {}", toolName, e);
                String errorMessage = e.getMessage() != null
                        ? e.getMessage()
                        : "工具执行失败: " + e.getClass().getSimpleName();

                // 记录失败 + 熔断反馈
                doRecord(toolName, paramsJson, null, errorMessage, duration, false);
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure(toolName);
                }

                return ToolResultBlock.error(errorMessage);
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * 转换执行结果为字符串
     */
    private String toStringResult(Object result) throws Exception {
        if (result == null) {
            return "执行成功，无返回结果";
        } else if (result instanceof String) {
            return (String) result;
        } else {
            return objectMapper.writeValueAsString(result);
        }
    }

    /**
     * 记录工具调用（recorder 为空时跳过）
     */
    private void doRecord(String toolName, String input, String output,
                          String error, long duration, boolean success) {
        if (recorder != null) {
            if (success) {
                recorder.record(ToolInvocationRecord.success(
                        toolName, toolDefinition.getCategory(), input, output, duration, null));
            } else {
                recorder.record(ToolInvocationRecord.failure(
                        toolName, toolDefinition.getCategory(), input, error, duration, null));
            }
        }
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

