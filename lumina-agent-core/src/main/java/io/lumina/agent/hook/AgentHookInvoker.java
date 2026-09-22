package io.lumina.agent.hook;

import io.lumina.agent.config.LuminaAgentProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 生命周期钩子调用器（组合 + 隔离 + 限额）
 *
 * <p>引擎与工具适配器经本类咨询钩子，不直接面对钩子列表。职责：
 * <ul>
 *   <li><b>组合</b>：多钩子按 {@code getOrder()} 升序执行——DENY 支配且短路、
 *       REPLACE_INPUT 首个生效、ADD_CONTEXT 累积、CONTINUE 首个生效</li>
 *   <li><b>隔离</b>：单钩子调用限时执行（守护线程池 + {@code timeout-ms}），
 *       超时/异常按<b>中立放行</b>处理并计数告警——钩子故障绝不崩回合；
 *       需要强一致的合规门应在实现内捕获自身故障并显式返回 deny</li>
 *   <li><b>限额</b>：reason / replacement / additionalContext 输出按配置截断，
 *       防钩子输出撑爆上下文</li>
 * </ul>
 *
 * <p>超时后线程仅作协作式中断（{@code cancel(true)}），卡死的实现线程不阻塞回合——
 * 与"钩子必须快速返回"的契约互为兜底。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
@Slf4j
@Component
public class AgentHookInvoker {

    private static final AtomicInteger THREAD_SEQ = new AtomicInteger();

    private final LuminaAgentProperties agentProperties;

    /** 按 Order 升序的不可变钩子列表 */
    private final List<AgentLifecycleHook> hooks;

    /** 守护线程池：钩子限时执行的载体，不占用轮次关键路径线程 */
    private final ExecutorService executor;

    @Nullable
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public AgentHookInvoker(LuminaAgentProperties agentProperties,
                            ObjectProvider<AgentLifecycleHook> hooksProvider,
                            ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistry) {
        this.agentProperties = agentProperties;
        // 显式按 getOrder() 排序（与 Spring orderedStream 语义一致且不依赖其实现）
        this.hooks = hooksProvider.orderedStream()
                .sorted(java.util.Comparator.comparingInt(AgentLifecycleHook::getOrder))
                .toList();
        this.meterRegistry = meterRegistry.getIfAvailable();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "agent-hook-" + THREAD_SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** 是否生效（全局开关开启且存在钩子 Bean） */
    public boolean isEnabled() {
        return agentProperties.getHooks().isEnabled() && !hooks.isEmpty();
    }

    /**
     * 用户输入提交前：DENY 支配短路；REPLACE_INPUT 首个生效（可与 ADD_CONTEXT 叠加）；
     * ADD_CONTEXT 累积合并
     */
    public HookDecision onUserPromptSubmit(AgentLifecycleHook.PromptSubmitInput input) {
        if (!isEnabled()) {
            return HookDecision.allow();
        }
        LuminaAgentProperties.HooksConfig config = agentProperties.getHooks();
        String denyReason = null;
        String denyHook = null;
        String replacement = null;
        StringBuilder contexts = new StringBuilder();
        for (AgentLifecycleHook hook : hooks) {
            HookDecision d = invoke(hook, "UserPromptSubmit", () -> hook.onUserPromptSubmit(input));
            if (d == null || d.action() == null) {
                continue;
            }
            switch (d.action()) {
                case DENY -> {
                    if (denyReason == null) {
                        denyReason = truncate(d.reason(), config.getMaxReasonChars());
                        denyHook = hook.getName();
                    }
                }
                case REPLACE_INPUT -> {
                    if (replacement == null && d.replacementInput() != null) {
                        replacement = truncate(d.replacementInput(), config.getMaxReplacementChars());
                    } else {
                        log.warn("钩子 {} 的 REPLACE_INPUT 被忽略（已有替换或替换文本为空）", hook.getName());
                    }
                }
                case ADD_CONTEXT -> appendContext(contexts, d.additionalContext(), config, hook);
                default -> log.warn("钩子 {} 在 UserPromptSubmit 返回无效动作 {}，按放行处理",
                        hook.getName(), d.action());
            }
            if (denyReason != null) {
                break;
            }
        }
        if (denyReason != null) {
            log.warn("生命周期钩子拒绝用户输入: hook={}, reason={}", denyHook, denyReason);
            return HookDecision.deny(denyReason);
        }
        String mergedContext = contexts.isEmpty() ? null : contexts.toString();
        if (replacement != null) {
            return new HookDecision(HookDecision.Action.REPLACE_INPUT, null, replacement, mergedContext);
        }
        if (mergedContext != null) {
            return HookDecision.addContext(mergedContext);
        }
        return HookDecision.allow();
    }

    /**
     * 会话首启：仅 ADD_CONTEXT 有效（会话首启不存在可拒绝对象），其余动作告警忽略
     */
    public HookDecision onSessionStart(AgentLifecycleHook.SessionStartInput input) {
        if (!isEnabled()) {
            return HookDecision.allow();
        }
        LuminaAgentProperties.HooksConfig config = agentProperties.getHooks();
        StringBuilder contexts = new StringBuilder();
        for (AgentLifecycleHook hook : hooks) {
            HookDecision d = invoke(hook, "SessionStart", () -> hook.onSessionStart(input));
            if (d == null || d.action() == null) {
                continue;
            }
            if (d.action() == HookDecision.Action.ADD_CONTEXT) {
                appendContext(contexts, d.additionalContext(), config, hook);
            } else if (d.action() != HookDecision.Action.ALLOW) {
                log.warn("钩子 {} 在 SessionStart 返回无效动作 {}（仅支持 ADD_CONTEXT），忽略",
                        hook.getName(), d.action());
            }
        }
        return contexts.isEmpty() ? HookDecision.allow() : HookDecision.addContext(contexts.toString());
    }

    /**
     * 工具执行前：DENY 支配（首个拒绝生效并短路），其余动作告警忽略
     */
    public HookDecision onPreToolUse(AgentLifecycleHook.ToolUseInput input) {
        if (!isEnabled()) {
            return HookDecision.allow();
        }
        LuminaAgentProperties.HooksConfig config = agentProperties.getHooks();
        for (AgentLifecycleHook hook : hooks) {
            HookDecision d = invoke(hook, "PreToolUse", () -> hook.onPreToolUse(input));
            if (d == null || d.action() == null) {
                continue;
            }
            if (d.action() == HookDecision.Action.DENY) {
                String reason = truncate(d.reason(), config.getMaxReasonChars());
                log.warn("生命周期钩子拒绝工具调用: hook={}, tool={}, reason={}",
                        hook.getName(), input.toolName(), reason);
                return HookDecision.deny(reason);
            }
            if (d.action() != HookDecision.Action.ALLOW) {
                log.warn("钩子 {} 在 PreToolUse 返回无效动作 {}（仅支持 DENY），按放行处理",
                        hook.getName(), d.action());
            }
        }
        return HookDecision.allow();
    }

    /** 工具执行成功后（纯观测通知） */
    public void onPostToolUse(AgentLifecycleHook.ToolResultInput input) {
        if (!isEnabled()) {
            return;
        }
        for (AgentLifecycleHook hook : hooks) {
            invoke(hook, "PostToolUse", () -> {
                hook.onPostToolUse(input);
                return null;
            });
        }
    }

    /** 工具执行失败后（纯观测通知） */
    public void onPostToolUseFailure(AgentLifecycleHook.ToolResultInput input) {
        if (!isEnabled()) {
            return;
        }
        for (AgentLifecycleHook hook : hooks) {
            invoke(hook, "PostToolUseFailure", () -> {
                hook.onPostToolUseFailure(input);
                return null;
            });
        }
    }

    /**
     * 轮次结束前：首个 CONTINUE 生效（理由作为继续指令），其余动作告警忽略；
     * 防循环上限由引擎在循环层强制
     */
    public HookDecision onStop(AgentLifecycleHook.StopInput input) {
        if (!isEnabled()) {
            return HookDecision.allow();
        }
        LuminaAgentProperties.HooksConfig config = agentProperties.getHooks();
        for (AgentLifecycleHook hook : hooks) {
            HookDecision d = invoke(hook, "Stop", () -> hook.onStop(input));
            if (d == null || d.action() == null) {
                continue;
            }
            if (d.action() == HookDecision.Action.CONTINUE) {
                String reason = truncate(
                        d.reason() != null && !d.reason().isBlank() ? d.reason() : "钩子未提供继续理由",
                        config.getMaxReasonChars());
                log.warn("生命周期钩子判定继续执行: hook={}, continueCount={}/{}, reason={}",
                        hook.getName(), input.continueCount(), input.maxContinues(), reason);
                return HookDecision.continueTurn(reason);
            }
            if (d.action() != HookDecision.Action.ALLOW) {
                log.warn("钩子 {} 在 Stop 返回无效动作 {}（仅支持 CONTINUE），按允许结束处理",
                        hook.getName(), d.action());
            }
        }
        return HookDecision.allow();
    }

    /**
     * 限时执行单钩子：超时/异常返回 null（调用方按中立处理），绝不向上抛
     */
    private <T> T invoke(AgentLifecycleHook hook, String point, Supplier<T> call) {
        long timeoutMs = agentProperties.getHooks().getTimeoutMs();
        Future<T> future = executor.submit(call::get);
        try {
            T result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            count(hook.getName(), point, "ok");
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("钩子超时（{}ms），按放行处理: hook={}, point={}", timeoutMs, hook.getName(), point);
            count(hook.getName(), point, "timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("钩子调用被中断，按放行处理: hook={}, point={}", hook.getName(), point);
            count(hook.getName(), point, "interrupted");
        } catch (Exception e) {
            log.warn("钩子执行异常，按放行处理: hook={}, point={}, error={}",
                    hook.getName(), point, e.getMessage());
            count(hook.getName(), point, "error");
        }
        return null;
    }

    private void appendContext(StringBuilder contexts, String context,
                               LuminaAgentProperties.HooksConfig config, AgentLifecycleHook hook) {
        if (context == null || context.isBlank()) {
            return;
        }
        String capped = truncate(context, config.getMaxContextChars());
        if (!contexts.isEmpty()) {
            contexts.append('\n');
        }
        contexts.append(capped);
        if (capped.length() < context.length()) {
            log.warn("钩子 {} 的 add-context 输出超限（{} -> {} 字符），已截断",
                    hook.getName(), context.length(), capped.length());
        }
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "…[钩子输出超长已截断]";
    }

    private void count(String hookName, String point, String outcome) {
        if (meterRegistry != null) {
            meterRegistry.counter("agent.hook.invocation",
                            "hook", hookName, "point", point, "outcome", outcome)
                    .increment();
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
