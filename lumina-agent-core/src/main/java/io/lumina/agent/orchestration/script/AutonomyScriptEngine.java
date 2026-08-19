package io.lumina.agent.orchestration.script;

import io.lumina.agent.orchestration.engine.AgentExecutionHandler;
import io.lumina.agent.orchestration.model.AutonomyNode;
import io.lumina.agent.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自主编排脚本引擎（GraalJS 沙箱）
 *
 * <p>执行 {@link AutonomyNode} 的 JS 编排脚本。沙箱策略：无宿主访问
 * （{@code Java.type}/类查找被拒）、无 IO、无进程/线程——脚本只能通过
 * 桥接函数与外界交互，攻击面收敛为"发起子 Agent 调用"。
 *
 * <p>桥接函数（guest 单线程模型：并发只发生在宿主侧 agent 调用）：
 * <ul>
 *   <li>{@code agent(prompt)} → 委托 {@link AgentExecutionHandler}（走完整
 *       安全管线与 Agent 自身工具白名单）；总量/并发限额，触顶即 fatal</li>
 *   <li>{@code parallel([prompt...])} → 宿主侧虚拟线程并行发起子调用，
 *       单项失败映射 null（覆盖 fan-out 主场景，且规避多线程 guest 访问限制）</li>
 *   <li>{@code pipeline(items, ...stages)} → 逐段加工（guest 顺序执行），
 *       单项失败该条为 null</li>
 *   <li>{@code log(msg)} → 服务器日志（观察，不影响执行）</li>
 * </ul>
 *
 * <p>边界纪律（照抄 dsh workflow-worker-thread）：返回值物化为纯 JSON
 * （拒函数/宿主对象/危险属性名）；超时先 interrupt 再 close(true)（有界宽限终止）；
 * 拼错选项/触顶限额绝不溶进普通失败。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@Component
@ConditionalOnBean(AgentExecutionHandler.class)
public class AutonomyScriptEngine {

    /** 物化时拒绝的属性名（防 __proto__ 类原型污染语义随结果外流） */
    private static final Set<String> FORBIDDEN_MEMBER_KEYS = Set.of("__proto__", "prototype", "constructor");

    private static final long TERMINATION_GRACE_MS = 3000;

    private final AgentExecutionHandler agentHandler;

    public AutonomyScriptEngine(AgentExecutionHandler agentHandler) {
        this.agentHandler = agentHandler;
    }

    /**
     * 执行脚本并返回物化结果（Map/List/字符串/数字/布尔/null）
     *
     * @param node  自主编排节点
     * @param input 解析后的节点输入（绑定到脚本的 input 变量）
     * @return 物化后的纯 JSON 值
     * @throws IllegalStateException 超时/触顶限额/脚本 fatal 错误
     */
    public Object run(AutonomyNode node, String input) {
        int maxTotal = orDefault(node.getMaxTotalAgents(), 20);
        int maxConcurrent = orDefault(node.getMaxConcurrentAgents(), 5);
        int maxItems = orDefault(node.getMaxItemsPerCall(), 200);
        long timeoutMs = orDefault(node.getTimeoutSeconds(), 120) * 1000L;

        AtomicLong totalCalls = new AtomicLong();
        Semaphore concurrency = new Semaphore(maxConcurrent);

        try (Context context = Context.newBuilder("js")
                .allowAllAccess(false)
                .option("js.ecmascript-version", "2022")
                .build()) {

            Value bindings = context.getBindings("js");
            bindings.putMember("input", input != null ? input : "");
            bindings.putMember("log", (ProxyExecutable) args -> {
                log.info("[autonomy {}] {}", node.getId(), args.length > 0 ? args[0].toString() : "");
                return null;
            });
            bindings.putMember("agent", (ProxyExecutable) args -> {
                if (args.length < 1 || !args[0].isString()) {
                    throw new IllegalArgumentException("agent(prompt) 需要一个字符串参数");
                }
                return callAgent(node, args[0].asString(), totalCalls, concurrency, maxTotal);
            });
            bindings.putMember("parallel", (ProxyExecutable) args -> {
                if (args.length < 1 || !args[0].hasArrayElements()) {
                    throw new IllegalArgumentException("parallel 需要字符串数组");
                }
                return toGuestArray(context, parallel(node, args[0], totalCalls, concurrency, maxTotal, maxItems));
            });
            bindings.putMember("pipeline", (ProxyExecutable) args ->
                    toGuestArray(context, pipeline(node, args, maxItems)));

            Value result = evalWithTimeout(context, node, timeoutMs);
            Object materialized = materialize(result, "返回值");
            log.info("[autonomy {}] 脚本完成, 子调用 {}/{}, 结果类型 {}",
                    node.getId(), totalCalls.get(), maxTotal,
                    materialized == null ? "null" : materialized.getClass().getSimpleName());
            return materialized;
        }
    }

    private String callAgent(AutonomyNode node, String prompt, AtomicLong totalCalls,
                             Semaphore concurrency, int maxTotal) {
        long used = totalCalls.incrementAndGet();
        if (used > maxTotal) {
            // cap 触顶必须响亮，绝不静默
            throw new IllegalStateException("超出子调用总量上限 " + maxTotal);
        }
        concurrency.acquireUninterruptibly();
        try {
            log.info("[autonomy {}] 子调用 {}/{}: {}", node.getId(), used, maxTotal,
                    prompt.length() > 80 ? prompt.substring(0, 80) + "..." : prompt);
            return agentHandler.executeAgent(node.getAgentId(), prompt, node.getConversationUuid());
        } finally {
            concurrency.release();
        }
    }

    /**
     * 把宿主侧 List 转为 guest 原生数组（JSON 往返）——Java 集合直接跨回
     * guest 会变成不透明宿主对象，破坏后续物化
     */
    private Value toGuestArray(Context context, List<Object> list) {
        try {
            String json = JsonUtils.OBJECT_MAPPER.writeValueAsString(list);
            return context.getBindings("js").getMember("JSON").getMember("parse").execute(json);
        } catch (Exception e) {
            throw new IllegalStateException("编排结果序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 并行发起子调用：prompt 数组 → 宿主侧虚拟线程并行；单项失败 → null
     */
    private List<Object> parallel(AutonomyNode node, Value prompts, AtomicLong totalCalls,
                                  Semaphore concurrency, int maxTotal, int maxItems) {
        long size = prompts.getArraySize();
        if (size > maxItems) {
            throw new IllegalStateException("parallel 条目 " + size + " 超出上限 " + maxItems);
        }
        List<String> items = new ArrayList<>((int) size);
        for (int i = 0; i < size; i++) {
            Value item = prompts.getArrayElement(i);
            if (!item.isString()) {
                throw new IllegalArgumentException("parallel 数组元素必须是字符串 prompt");
            }
            items.add(item.asString());
        }
        List<Object> results = new ArrayList<>(items.size());
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<Object>> futures = new ArrayList<>(items.size());
            for (String prompt : items) {
                futures.add(pool.submit(() -> {
                    try {
                        return callAgent(node, prompt, totalCalls, concurrency, maxTotal);
                    } catch (Exception e) {
                        // 单项失败映射 null，绝不溶进整体 fatal
                        log.warn("[autonomy {}] parallel 子调用失败, 置 null: {}", node.getId(), e.getMessage());
                        return null;
                    }
                }));
            }
            for (Future<Object> future : futures) {
                try {
                    results.add(future.get());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("parallel 等待被中断", ie);
                } catch (java.util.concurrent.ExecutionException ee) {
                    throw wrapFatal("parallel 子调用异常", ee);
                }
            }
            return results;
        } catch (Exception e) {
            throw wrapFatal("parallel 执行失败", e);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * pipeline(items, ...stages)：逐段加工（guest 顺序执行）；单项在某段失败 → 该项 null
     */
    private List<Object> pipeline(AutonomyNode node, Value[] args, int maxItems) {
        if (args.length < 2) {
            throw new IllegalArgumentException("pipeline(items, ...stages) 至少需要一个 stage");
        }
        Value items = args[0];
        if (!items.hasArrayElements()) {
            throw new IllegalArgumentException("pipeline 第一参数必须是数组");
        }
        long size = items.getArraySize();
        if (size > maxItems) {
            throw new IllegalStateException("pipeline 条目 " + size + " 超出上限 " + maxItems);
        }
        List<Value> stages = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].canExecute()) {
                throw new IllegalArgumentException("pipeline 的 stage 必须是函数");
            }
            stages.add(args[i]);
        }
        List<Object> results = new ArrayList<>((int) size);
        for (int i = 0; i < size; i++) {
            Value current = items.getArrayElement(i);
            boolean failed = false;
            for (Value stage : stages) {
                if (failed) {
                    break;
                }
                try {
                    current = stage.execute(current);
                } catch (Exception e) {
                    log.warn("[autonomy {}] pipeline 第 {} 项在某段失败, 置 null: {}",
                            node.getId(), i, e.getMessage());
                    failed = true;
                }
            }
            results.add(failed ? null : materialize(current, "pipeline 结果"));
        }
        return results;
    }

    /**
     * 超时控制：先 interrupt（有界宽限），仍不退出则 close(true) 强制终止
     *
     * <p>脚本包裹为 IIFE，使"以 return 结束"成为合法契约（顶层 return 在
     * script 模式下非法）。
     */
    private Value evalWithTimeout(Context context, AutonomyNode node, long timeoutMs) {
        String wrapped = "(function(){\n" + node.getScript() + "\n})()";
        ExecutorService runner = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
        try {
            Future<Value> future = runner.submit(() -> context.eval("js", wrapped));
            try {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                try {
                    context.interrupt(java.time.Duration.ofMillis(TERMINATION_GRACE_MS));
                } catch (TimeoutException interruptGraceExpired) {
                    // interrupt 宽限本身超时，走强制关闭
                }
                try {
                    return future.get(TERMINATION_GRACE_MS, TimeUnit.MILLISECONDS);
                } catch (Exception ignored) {
                    // 宽限后仍卡死，强制终止
                }
                closeForcibly(context);
                throw new IllegalStateException("autonomy 脚本执行超时（" + timeoutMs / 1000 + "s），已强制终止");
            } catch (Exception e) {
                throw wrapFatal("autonomy 脚本执行失败", e);
            }
        } finally {
            runner.shutdownNow();
        }
    }

    private void closeForcibly(Context context) {
        try {
            context.close(true);
        } catch (Exception e) {
            log.warn("强制关闭脚本上下文失败: {}", e.getMessage());
        }
    }

    /**
     * 返回值物化为纯 JSON（Map/List/字符串/数字/布尔/null）
     *
     * <p>拒绝：函数（canExecute）、宿主对象、危险属性名（__proto__/prototype/constructor）。
     */
    Object materialize(Value value, String where) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }
        if (value.canExecute()) {
            throw new IllegalArgumentException(where + " 不能包含函数（必须 return 纯 JSON）");
        }
        if (value.hasArrayElements()) {
            long size = value.getArraySize();
            if (size > 10_000) {
                throw new IllegalArgumentException(where + " 数组过大（" + size + " > 10000）");
            }
            List<Object> list = new ArrayList<>((int) size);
            for (int i = 0; i < size; i++) {
                list.add(materialize(value.getArrayElement(i), where));
            }
            return list;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new HashMap<>();
            for (String key : value.getMemberKeys()) {
                if (FORBIDDEN_MEMBER_KEYS.contains(key)) {
                    throw new IllegalArgumentException(where + " 含非法属性名: " + key);
                }
                map.put(key, materialize(value.getMember(key), where));
            }
            return map;
        }
        throw new IllegalArgumentException(where + " 包含不可物化的类型");
    }

    private IllegalStateException wrapFatal(String message, Exception e) {
        Throwable cause = e instanceof java.util.concurrent.ExecutionException exe && exe.getCause() != null
                ? exe.getCause() : e;
        return new IllegalStateException(message + ": " + cause.getMessage(), cause);
    }

    private int orDefault(Integer value, int def) {
        return value != null && value > 0 ? value : def;
    }
}
