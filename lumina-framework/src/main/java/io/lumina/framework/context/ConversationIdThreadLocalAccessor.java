package io.lumina.framework.context;

import io.lumina.common.core.BaseContext;
import io.micrometer.context.ThreadLocalAccessor;

/**
 * conversationId 的 ThreadLocalAccessor
 *
 * <p>将 {@link BaseContext} 的 conversationId ThreadLocal 接入 Micrometer Context Propagation，
 * 使会话 ID 在 Reactor 线程切换（subscribeOn / boundedElastic 等）时自动捕获与重放，
 * 保障工具调用记录能正确关联会话上下文。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
public class ConversationIdThreadLocalAccessor implements ThreadLocalAccessor<String> {

    public static final String KEY = "lumina.conversation-id";

    @Override
    public String getValue() {
        return BaseContext.getConversationId();
    }

    @Override
    public void setValue(String value) {
        BaseContext.setConversationId(value);
    }

    @Override
    public void setValue() {
        BaseContext.clearConversationId();
    }

    @Override
    public String key() {
        return KEY;
    }
}
