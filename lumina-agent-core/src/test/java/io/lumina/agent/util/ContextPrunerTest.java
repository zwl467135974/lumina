package io.lumina.agent.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContextPruner 单元测试
 *
 * @author Lumina Team
 * @since 3.11.0
 */
class ContextPrunerTest {

    @Test
    void pruneReturnsOriginalWhenUnderThreshold() {
        String content = "a".repeat(100);
        assertThat(ContextPruner.prune(content, 200, 50, 20)).isSameAs(content);
    }

    @Test
    void pruneReturnsOriginalForNull() {
        assertThat(ContextPruner.prune(null, 200, 50, 20)).isNull();
    }

    @Test
    void pruneKeepsHeadAndTailWithMarker() {
        String content = "H".repeat(3000) + "M".repeat(4000) + "T".repeat(3000);
        String pruned = ContextPruner.prune(content, 4000, 100, 50);

        assertThat(pruned).startsWith("H".repeat(100));
        assertThat(pruned).endsWith("T".repeat(50));
        assertThat(pruned).contains("已省略 9850 字符");
        // 修剪后长度远小于原文
        assertThat(pruned.length()).isLessThan(300);
    }

    @Test
    void pruneHandlesOversizedKeepParams() {
        // head+tail 超过原文长度时不应越界
        String content = "abc";
        String pruned = ContextPruner.prune(content, 2, 100, 100);
        assertThat(pruned).contains("abc");
    }
}
