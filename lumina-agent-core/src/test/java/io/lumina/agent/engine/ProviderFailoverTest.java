package io.lumina.agent.engine;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ProviderFailover 单元测试
 *
 * <p>验证 failover 链的核心逻辑：成功直通、可重试异常切换、不可重试异常终止。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
class ProviderFailoverTest {

    private Msg dummyMsg() {
        return Msg.builder().role(MsgRole.ASSISTANT).textContent("ok").build();
    }

    @Test
    void firstProviderSucceedsNoFailover() {
        Msg result = ProviderFailover.executeWithFailover(
                List.of(() -> dummyMsg()),
                List.of("primary"));

        assertThat(result.getTextContent()).isEqualTo("ok");
    }

    @Test
    void timeoutTriggersFailover() {
        Msg result = ProviderFailover.executeWithFailover(
                List.of(
                        () -> { throw new RuntimeException("connection timeout"); },
                        () -> dummyMsg()
                ),
                List.of("primary", "fallback"));

        assertThat(result.getTextContent()).isEqualTo("ok");
    }

    @Test
    void rateLimitTriggersFailover() {
        Msg result = ProviderFailover.executeWithFailover(
                List.of(
                        () -> { throw new RuntimeException("429 Too Many Requests"); },
                        () -> dummyMsg()
                ),
                List.of("primary", "fallback"));

        assertThat(result.getTextContent()).isEqualTo("ok");
    }

    @Test
    void authErrorDoesNotTriggerFailover() {
        // 鉴权错误不可重试，应直接抛出
        assertThatThrownBy(() -> ProviderFailover.executeWithFailover(
                List.of(
                        () -> { throw new RuntimeException("401 Unauthorized: invalid api key"); },
                        () -> dummyMsg()
                ),
                List.of("primary", "fallback")))
                .hasMessageContaining("401");
    }

    @Test
    void allProvidersFailThrowsLastException() {
        assertThatThrownBy(() -> ProviderFailover.executeWithFailover(
                List.of(
                        () -> { throw new RuntimeException("503 timeout"); },
                        () -> { throw new RuntimeException("connection refused"); }
                ),
                List.of("primary", "fallback")))
                .hasMessageContaining("所有 Provider 均失败");
    }

    @Test
    void emptyChainThrowsIllegalArgument() {
        assertThatThrownBy(() -> ProviderFailover.executeWithFailover(List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullResponseTriggersFailover() {
        Msg result = ProviderFailover.executeWithFailover(
                List.of(
                        () -> null,
                        () -> dummyMsg()
                ),
                List.of("primary", "fallback"));

        assertThat(result.getTextContent()).isEqualTo("ok");
    }

    @Test
    void isFailoverEligibleDetectsNetworkErrors() {
        assertThat(ProviderFailover.isFailoverEligible(new java.io.IOException("broken pipe"))).isTrue();
        assertThat(ProviderFailover.isFailoverEligible(new java.util.concurrent.TimeoutException())).isTrue();
        assertThat(ProviderFailover.isFailoverEligible(new RuntimeException("502 Bad Gateway"))).isTrue();
    }

    @Test
    void isFailoverEligibleRejectsBusinessErrors() {
        assertThat(ProviderFailover.isFailoverEligible(new RuntimeException("invalid parameter"))).isFalse();
        assertThat(ProviderFailover.isFailoverEligible(new RuntimeException("401 Unauthorized"))).isFalse();
    }
}
