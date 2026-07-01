package io.lumina.agent.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemoryManager 单元测试（内存模式，RedisTemplate 未注入）
 *
 * @author Lumina Team
 * @since 1.1.0
 */
class MemoryManagerTest {

    private MemoryManager manager;

    @BeforeEach
    void setUp() {
        manager = new MemoryManager();
    }

    @Test
    void addAndRetrieveMemory() {
        manager.addMemory("s1", "user", "hello");

        List<MemoryManager.Memory> memories = manager.getMemories("s1");
        assertThat(memories).hasSize(1);
        assertThat(memories.get(0).content()).isEqualTo("hello");
        assertThat(memories.get(0).role()).isEqualTo("user");
        assertThat(memories.get(0).timestamp()).isPositive();
    }

    @Test
    void multipleMemoriesOrderedByInsertion() {
        manager.addMemory("s1", "user", "first");
        manager.addMemory("s1", "assistant", "second");

        List<MemoryManager.Memory> memories = manager.getMemories("s1");
        assertThat(memories).hasSize(2);
        assertThat(memories.get(0).content()).isEqualTo("first");
        assertThat(memories.get(1).content()).isEqualTo("second");
    }

    @Test
    void emptySessionReturnsEmptyList() {
        assertThat(manager.getMemories("nonexistent")).isEmpty();
    }

    @Test
    void clearMemories() {
        manager.addMemory("s1", "user", "data");
        manager.clearMemories("s1");

        assertThat(manager.getMemories("s1")).isEmpty();
    }

    @Test
    void getRecentMemoriesReturnsLastN() {
        for (int i = 0; i < 10; i++) {
            manager.addMemory("s1", "user", "msg" + i);
        }

        List<MemoryManager.Memory> recent = manager.getRecentMemories("s1", 3);
        assertThat(recent).hasSize(3);
        assertThat(recent.get(0).content()).isEqualTo("msg7");
        assertThat(recent.get(2).content()).isEqualTo("msg9");
    }

    @Test
    void getRecentMemoriesWhenFewerThanN() {
        manager.addMemory("s1", "user", "only");

        List<MemoryManager.Memory> recent = manager.getRecentMemories("s1", 5);
        assertThat(recent).hasSize(1);
    }

    @Test
    void maxMemorySizeEvictsOldest() {
        for (int i = 0; i < 105; i++) {
            manager.addMemory("s1", "user", "msg" + i);
        }

        List<MemoryManager.Memory> memories = manager.getMemories("s1");
        assertThat(memories).hasSize(100);
        assertThat(memories.get(0).content()).isEqualTo("msg5");
        assertThat(memories.get(99).content()).isEqualTo("msg104");
    }

    @Test
    void sessionsAreIsolated() {
        manager.addMemory("s1", "user", "session1");
        manager.addMemory("s2", "user", "session2");

        assertThat(manager.getMemories("s1")).hasSize(1);
        assertThat(manager.getMemories("s2")).hasSize(1);

        manager.clearMemories("s1");
        assertThat(manager.getMemories("s1")).isEmpty();
        assertThat(manager.getMemories("s2")).hasSize(1);
    }
}
