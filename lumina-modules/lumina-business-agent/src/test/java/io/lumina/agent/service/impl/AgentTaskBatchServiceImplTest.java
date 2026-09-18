package io.lumina.agent.service.impl;

import io.lumina.agent.api.dto.BatchTaskDTO;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentTaskBatchServiceImpl 单元测试（确定性拆分策略与工程拼接合并）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
class AgentTaskBatchServiceImplTest {

    @Test
    void splitByLinesGroupsLinesIntoBundles() {
        String text = "l1\nl2\nl3\nl4\nl5";
        List<String> bundles = AgentTaskBatchServiceImpl.splitByLines(text, 2);

        assertThat(bundles).hasSize(3);
        assertThat(bundles.get(0)).isEqualTo("l1\nl2");
        assertThat(bundles.get(1)).isEqualTo("l3\nl4");
        assertThat(bundles.get(2)).isEqualTo("l5");
    }

    @Test
    void splitByLinesNormalizesCrlf() {
        List<String> bundles = AgentTaskBatchServiceImpl.splitByLines("a\r\nb\r\nc", 10);
        assertThat(bundles).hasSize(1);
        assertThat(bundles.get(0)).isEqualTo("a\nb\nc");
    }

    @Test
    void splitByCharsAddsOverlap() {
        String text = "0123456789ABCDEFGHIJ"; // 20 字符
        List<String> bundles = AgentTaskBatchServiceImpl.splitByChars(text, 10);

        // 步进 = bundleSize - overlap(1) = 9：片1 [0,10)，片2 [9,19)，片3 [18,20)
        assertThat(bundles).hasSize(3);
        assertThat(bundles.get(0)).isEqualTo("0123456789");
        assertThat(bundles.get(1)).isEqualTo("9ABCDEFGHI");
        assertThat(bundles.get(2)).isEqualTo("IJ");
    }

    @Test
    void splitByCharsDropsBlankPieces() {
        List<String> bundles = AgentTaskBatchServiceImpl.splitByChars("   ", 2);
        assertThat(bundles).isEmpty();
    }

    @Test
    void splitDispatchesByStrategy() {
        BatchTaskDTO dto = new BatchTaskDTO();
        dto.setInputText("a\nb");
        dto.setBundleSize(1);
        dto.setSplitStrategy("BY_LINES");
        assertThat(AgentTaskBatchServiceImpl.split(dto)).hasSize(2);

        dto.setSplitStrategy("BY_CHARS");
        dto.setInputText("abcdef");
        dto.setBundleSize(3);
        assertThat(AgentTaskBatchServiceImpl.split(dto)).hasSize(3);
    }

    @Test
    void concatResultsKeepsBundleOrderAndHeaders() {
        AgentTaskDO c0 = child(0, "结果一");
        AgentTaskDO c1 = child(1, "结果二");
        AgentTaskDO c2 = child(2, "结果三"); // FAILED，不参与

        String merged = AgentTaskBatchServiceImpl.concatResults("指令", List.of(c0, c1), 3);

        assertThat(merged).isEqualTo("=== 分片 1/3 ===\n结果一\n\n=== 分片 2/3 ===\n结果二");
    }

    private AgentTaskDO child(int index, String result) {
        AgentTaskDO task = new AgentTaskDO();
        task.setBundleIndex(index);
        task.setStatus("COMPLETED");
        task.setResult(result);
        return task;
    }
}
