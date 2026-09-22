package io.lumina.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.lumina.agent.hook.AgentHookInvoker;
import io.lumina.agent.hook.AgentLifecycleHook;
import io.lumina.agent.hook.HookDecision;
import io.lumina.agent.monitor.ToolCircuitBreaker;
import io.lumina.agent.monitor.ToolInvocationRecord;
import io.lumina.agent.monitor.ToolInvocationRecorder;
import io.lumina.agent.tool.security.ToolExecutionContext;
import io.lumina.agent.tool.security.ToolSecurityPipeline;
import io.lumina.agent.tool.spill.ToolResultSpiller;
import io.lumina.agent.util.JsonUtils;
import io.lumina.common.core.BaseContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * ToolDefinition 到 AgentTool 的适配器
 *
 * <p>将 Lumina 的 ToolDefinition 适配为 AgentScope 的 AgentTool 接口实现。
 * 这样可以将 EnhancedToolManager 管理的工具动态注册到 AgentScope Toolkit。
 *
 * <p>执行管线（3.11.0 起）：熔断检查 → 安全管线（拦截器→审批→单调守卫）
 * → 工具体 → 超时。安全拒绝不计入熔断（策略否决不是工具故障），
 * 拒绝理由对模型可见以便自纠。
 *
 * <p>决策型生命周期 Hook（3.14.0 起）：PreToolUse 在安全管线之前（企业合规门
 * 便宜检查先行，避免先进人工审批再被拒）；PostToolUse / PostToolUseFailure
 * 在结果外存化之后（钩子看到的是模型可见的最终结果）。
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
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final long executionTimeoutMs;
    private final ToolSecurityPipeline securityPipeline;
    private final ToolResultSpiller resultSpiller;
    private final AgentHookInvoker hookInvoker;
    private final io.lumina.agent.steering.SteeringMessageStore steeringStore;
    private Map<String, Object> parametersSchema;

    public ToolDefinitionToAgentToolAdapter(ToolDefinition toolDefinition) {
        this(toolDefinition, null, null, null, 60000);
    }

    public ToolDefinitionToAgentToolAdapter(ToolDefinition toolDefinition,
                                            ToolInvocationRecorder recorder,
                                            ToolCircuitBreaker circuitBreaker,
                                            io.micrometer.core.instrument.MeterRegistry meterRegistry,
                                            long executionTimeoutMs) {
        this(toolDefinition, recorder, circuitBreaker, meterRegistry, executionTimeoutMs, null);
    }

    public ToolDefinitionToAgentToolAdapter(ToolDefinition toolDefinition,
                                            ToolInvocationRecorder recorder,
                                            ToolCircuitBreaker circuitBreaker,
                                            io.micrometer.core.instrument.MeterRegistry meterRegistry,
                                            long executionTimeoutMs,
                                            ToolSecurityPipeline securityPipeline) {
        this(toolDefinition, recorder, circuitBreaker, meterRegistry, executionTimeoutMs,
                securityPipeline, null);
    }

    public ToolDefinitionToAgentToolAdapter(ToolDefinition toolDefinition,
                                            ToolInvocationRecorder recorder,
                                            ToolCircuitBreaker circuitBreaker,
                                            io.micrometer.core.instrument.MeterRegistry meterRegistry,
                                            long executionTimeoutMs,
                                            ToolSecurityPipeline securityPipeline,
                                            ToolResultSpiller resultSpiller) {
        this(toolDefinition, recorder, circuitBreaker, meterRegistry, executionTimeoutMs,
                securityPipeline, resultSpiller, null);
    }

    public ToolDefinitionToAgentToolAdapter(ToolDefinition toolDefinition,
                                            ToolInvocationRecorder recorder,
                                            ToolCircuitBreaker circuitBreaker,
                                            io.micrometer.core.instrument.MeterRegistry meterRegistry,
                                            long executionTimeoutMs,
                                            ToolSecurityPipeline securityPipeline,
                                            ToolResultSpiller resultSpiller,
                                            AgentHookInvoker hookInvoker) {
        this(toolDefinition, recorder, circuitBreaker, meterRegistry, executionTimeoutMs,
                securityPipeline, resultSpiller, hookInvoker, null);
    }

    public ToolDefinitionToAgentToolAdapter(ToolDefinition toolDefinition,
                                            ToolInvocationRecorder recorder,
                                            ToolCircuitBreaker circuitBreaker,
                                            io.micrometer.core.instrument.MeterRegistry meterRegistry,
                                            long executionTimeoutMs,
                                            ToolSecurityPipeline securityPipeline,
                                            ToolResultSpiller resultSpiller,
                                            AgentHookInvoker hookInvoker,
                                            io.lumina.agent.steering.SteeringMessageStore steeringStore) {
        this.toolDefinition = toolDefinition;
        this.recorder = recorder;
        this.circuitBreaker = circuitBreaker;
        this.meterRegistry = meterRegistry;
        this.executionTimeoutMs = executionTimeoutMs;
        this.securityPipeline = securityPipeline;
        this.resultSpiller = resultSpiller;
        this.hookInvoker = hookInvoker;
        this.steeringStore = steeringStore;
        this.objectMapper = JsonUtils.OBJECT_MAPPER;
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
                    return buildErrorResult(msg, paramsJson);
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

                // 决策型生命周期 Hook（v3.14 批次 3.1）：PreToolUse 在安全管线之前——
                // 便宜的企业合规检查先行，避免先进入人工审批再被钩子拒绝；拒绝理由对模型可见
                if (hookInvoker != null && hookInvoker.isEnabled()) {
                    HookDecision hookDecision = hookInvoker.onPreToolUse(new AgentLifecycleHook.ToolUseInput(
                            toolName, toolDefinition.getCategory(), paramsJson,
                            BaseContext.getConversationId()));
                    if (hookDecision.denied()) {
                        String msg = "工具调用被生命周期钩子拒绝: " + hookDecision.reason();
                        doRecord(toolName, paramsJson, null, msg, System.currentTimeMillis() - start, false);
                        return buildErrorResult(msg, paramsJson);
                    }
                }

                // 安全管线（拦截器 → 审批 → 单调守卫）；拒绝不计入熔断（策略否决不是工具故障）
                if (securityPipeline != null) {
                    String denial = securityPipeline.check(new ToolExecutionContext(
                            toolName, toolDefinition.getCategory(), paramsJson,
                            BaseContext.getConversationId(), BaseContext.getTenantId(),
                            BaseContext.getUserId(), toolDefinition.getReadOnlyHint()));
                    if (denial != null) {
                        String msg = "工具调用被安全策略拒绝: " + denial;
                        doRecord(toolName, paramsJson, null, msg, System.currentTimeMillis() - start, false);
                        return buildErrorResult(msg, paramsJson);
                    }
                }

                log.debug("执行工具: {}, 参数: {}", toolName, paramsJson);

                // 执行工具
                Object result = toolDefinition.execute(paramsJson);
                String resultString = toStringResult(result);

                // 超大结果外存化：模型侧只留预览 + 存档 ID（记录同样有界）
                if (resultSpiller != null) {
                    resultString = resultSpiller.spillIfNeeded(toolName,
                            BaseContext.getConversationId(), resultString);
                }

                // 运行中转向（v3.14 批次 3.2）：工具结果搭车转达——ReAct 循环内即时生效，
                // 每个结果至多搭一条（一次性消费）；位于 PostToolUse 钩子之前，钩子看到最终可见内容
                if (steeringStore != null) {
                    String conversationId = BaseContext.getConversationId();
                    if (conversationId != null) {
                        java.util.List<String> steering = steeringStore.drain(conversationId, 1);
                        if (!steering.isEmpty()) {
                            resultString = resultString
                                    + "\n\n[用户运行中转向指令] " + steering.get(0)
                                    + "\n（此指令由平台在工具结果中转达，优先级高于原任务描述，请据此调整后续行动）";
                        }
                    }
                }

                long duration = System.currentTimeMillis() - start;
                log.debug("工具执行完成: {}, 耗时: {}ms", toolName, duration);

                // PostToolUse 观测：看到的是模型可见的最终结果（含 spill 预览替换）
                if (hookInvoker != null && hookInvoker.isEnabled()) {
                    hookInvoker.onPostToolUse(new AgentLifecycleHook.ToolResultInput(
                            toolName, toolDefinition.getCategory(), paramsJson, resultString,
                            null, duration, BaseContext.getConversationId()));
                }

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

                // PostToolUseFailure 观测（策略拒绝不走此处——被拒的调用没有"执行"）
                if (hookInvoker != null && hookInvoker.isEnabled()) {
                    hookInvoker.onPostToolUseFailure(new AgentLifecycleHook.ToolResultInput(
                            toolName, toolDefinition.getCategory(), paramsJson, null,
                            errorMessage, duration, BaseContext.getConversationId()));
                }

                // 记录失败 + 熔断反馈
                doRecord(toolName, paramsJson, null, errorMessage, duration, false);
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure(toolName);
                }

                // 构建可操作的错误消息：包含错误原因 + 参数 schema 提示
                // 让 LLM 在下一轮 ReAct 循环中能据此修正参数重试
                return buildErrorResult(errorMessage, paramsJson);
            }
        })
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .timeout(java.time.Duration.ofMillis(executionTimeoutMs))
        .onErrorResume(TimeoutException.class, ex -> {
            String toolName = getName();
            long duration = executionTimeoutMs;
            String msg = "工具执行超时（" + (executionTimeoutMs / 1000) + "s）: " + toolName;
            log.warn(msg);
            if (hookInvoker != null && hookInvoker.isEnabled()) {
                hookInvoker.onPostToolUseFailure(new AgentLifecycleHook.ToolResultInput(
                        toolName, toolDefinition.getCategory(), "{}", null,
                        msg, duration, BaseContext.getConversationId()));
            }
            doRecord(toolName, "{}", null, msg, duration, false);
            if (circuitBreaker != null) {
                circuitBreaker.recordFailure(toolName);
            }
            return Mono.just(buildErrorResult(msg, "{}"));
        });
    }

    /**
     * 构建错误结果——包含可操作的提示信息
     *
     * <p>错误消息格式：原始错误 + 参数 schema，让 LLM 在下一轮 ReAct 循环中
     * 能理解失败原因并修正参数重试。
     *
     * <p>用 builder 正确设置 state=ERROR（ToolResultBlock.error() 不设置 state）。
     *
     * @param errorMsg   原始错误消息
     * @param paramsJson 实际传入的参数 JSON
     * @return 带 ERROR state 和提示信息的 ToolResultBlock
     */
    private ToolResultBlock buildErrorResult(String errorMsg, String paramsJson) {
        StringBuilder hint = new StringBuilder();
        hint.append("Error: ").append(errorMsg);

        // 附加参数 schema 提示（帮助 LLM 修正参数）
        if (parametersSchema != null && !parametersSchema.isEmpty()) {
            try {
                String schemaJson = objectMapper.writeValueAsString(parametersSchema);
                hint.append("\n\nExpected parameters schema: ").append(schemaJson);
            } catch (Exception ignored) {
                // schema 序列化失败不影响错误返回
            }
        }

        // 附加实际传入的参数（帮助 LLM 对比差异）
        if (paramsJson != null && !paramsJson.equals("{}")) {
            hint.append("\n\nYour input was: ").append(paramsJson);
        }

        hint.append("\n\nPlease check the parameter types and format, then retry.");

        return ToolResultBlock.builder()
                .output(io.agentscope.core.message.TextBlock.builder().text(hint.toString()).build())
                .state(io.agentscope.core.message.ToolResultState.ERROR)
                .build();
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
        String conversationId = BaseContext.getConversationId();
        if (meterRegistry != null) {
            meterRegistry.timer("tool.invocation.duration",
                    "name", toolName,
                    "result", success ? "success" : "failure")
                    .record(java.time.Duration.ofMillis(duration));
        }
        if (recorder != null) {
            if (success) {
                recorder.record(ToolInvocationRecord.success(
                        toolName, toolDefinition.getCategory(), input, output, duration, conversationId));
            } else {
                recorder.record(ToolInvocationRecord.failure(
                        toolName, toolDefinition.getCategory(), input, error, duration, conversationId));
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

