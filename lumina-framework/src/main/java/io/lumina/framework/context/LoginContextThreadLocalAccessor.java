package io.lumina.framework.context;

import io.lumina.common.core.BaseContext;
import io.lumina.common.core.LoginContext;
import io.micrometer.context.ThreadLocalAccessor;

/**
 * {@link LoginContext} 的 ThreadLocalAccessor
 *
 * <p>将 {@link BaseContext} 的 ThreadLocal 接入 Micrometer Context Propagation，
 * 使登录上下文（租户/用户/角色/权限）在 Reactor 线程切换（subscribeOn / boundedElastic 等）时
 * 自动捕获与重放，修复 Agent 执行引擎与工具调用在异步线程中上下文丢失导致的租户隔离失效问题。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public class LoginContextThreadLocalAccessor implements ThreadLocalAccessor<LoginContext> {

    /**
     * 上下文注册键
     */
    public static final String KEY = "lumina.login-context";

    @Override
    public LoginContext getValue() {
        return BaseContext.current();
    }

    @Override
    public void setValue(LoginContext value) {
        BaseContext.setCurrent(value);
    }

    /**
     * 清除当前线程的上下文（Reactor 链路结束时回调，避免线程池线程复用导致的上下文残留）
     */
    @Override
    public void setValue() {
        BaseContext.setCurrent(null);
    }

    @Override
    public String key() {
        return KEY;
    }
}
