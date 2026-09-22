package io.lumina.agent.steering;

import io.lumina.agent.config.LuminaAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内存版转向消息存储单元测试
 *
 * @author Lumina Team
 * @since 3.14.0
 */
class InMemorySteeringMessageStoreTest {

    private LuminaAgentProperties props;
    private InMemorySteeringMessageStore store;

    @BeforeEach
    void setUp() {
        props = new LuminaAgentProperties();
        store = new InMemorySteeringMessageStore(props);
    }

    @Test
    void drainIsOneShotConsumption() {
        store.offer("c1", "先看文档");
        store.offer("c1", "再看代码");

        assertThat(store.drain("c1")).containsExactly("先看文档", "再看代码");
        assertThat(store.drain("c1")).isEmpty();
    }

    @Test
    void drainRespectsMax() {
        store.offer("c1", "m1");
        store.offer("c1", "m2");
        store.offer("c1", "m3");

        List<String> drained = store.drain("c1", 2);

        assertThat(drained).containsExactly("m1", "m2");
        assertThat(store.drain("c1")).containsExactly("m3");
    }

    @Test
    void nullOrBlankInputsIgnored() {
        store.offer(null, "无会话");
        store.offer("c1", null);
        store.offer("c1", "  ");

        assertThat(store.drain("c1")).isEmpty();
    }

    @Test
    void oversizedMessageTruncated() {
        props.getSteering().setMaxMessageChars(100);
        store.offer("c1", "x".repeat(500));

        List<String> drained = store.drain("c1");

        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).length()).isLessThanOrEqualTo(100 + 20);
        assertThat(drained.get(0)).contains("已截断");
    }

    @Test
    void unknownConversationDrainsEmpty() {
        assertThat(store.drain("nobody")).isEmpty();
        assertThat(store.drain(null)).isEmpty();
        assertThat(store.drain("c1", 0)).isEmpty();
    }

    @Test
    void conversationsAreIsolated() {
        store.offer("c1", "给一会话");
        store.offer("c2", "给二会话");

        assertThat(store.drain("c1")).containsExactly("给一会话");
        assertThat(store.drain("c2")).containsExactly("给二会话");
    }
}
