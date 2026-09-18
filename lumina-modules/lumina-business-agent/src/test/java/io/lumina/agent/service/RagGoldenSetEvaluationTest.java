package io.lumina.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.BaseIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 检索质量评测（golden set 回归）
 *
 * <p>回答 v3.12 评审缺口 P1-7："RAG 无评测集——知识飞轮变厚但无法证明变好"。
 * 以人工标注的 golden set（查询 → 期望命中文档）量化检索质量：hit@k 与 MRR，
 * 防止分块策略/模型/召回参数的改动造成质量静默劣化。
 *
 * <p>环境门控：未设置 {@code LUMINA_RAG_GOLDEN_SET}（JSONL 文件路径）时整体
 * 跳过——评测需要真实 Qdrant + 向量化环境与标注数据，不适合无差别跑在 CI。
 * 使用方式与数据格式见 {@code docs/zh/guides/RAG评测集.md}。
 *
 * @author Lumina Team
 * @since 3.12.1
 */
@TestPropertySource(properties = "spring.test.context.cache.maxSize=1")
class RagGoldenSetEvaluationTest extends BaseIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static List<JsonNode> cases;
    private static String goldenSetSource;

    @Autowired
    private KnowledgeService knowledgeService;

    @BeforeAll
    static void loadGoldenSet() throws Exception {
        String path = System.getenv("LUMINA_RAG_GOLDEN_SET");
        Assumptions.assumeTrue(path != null && !path.isBlank(),
                "未设置 LUMINA_RAG_GOLDEN_SET，跳过 RAG 评测（用法见 docs/zh/guides/RAG评测集.md）");
        goldenSetSource = path;
        List<JsonNode> loaded = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(path))) {
            if (!line.isBlank() && !line.trim().startsWith("#")) {
                loaded.add(MAPPER.readTree(line));
            }
        }
        Assumptions.assumeTrue(!loaded.isEmpty(), "golden set 为空，跳过");
        cases = loaded;
    }

    @Test
    void retrievalQualityMeetsGoldenSet() {
        double sumMrr = 0;
        int hitCount = 0;
        List<String> failures = new ArrayList<>();

        for (JsonNode c : cases) {
            String query = c.path("query").asText();
            int limit = c.path("limit").asInt(5);
            List<String> expected = new ArrayList<>();
            c.path("expectedDocUuids").forEach(n -> expected.add(n.asText()));
            int minHitAtK = c.path("minHitAtK").asInt(1);

            List<Map<String, Object>> results = knowledgeService.search(query, limit);
            int rank = findFirstExpectedRank(results, expected);

            boolean hit = rank > 0;
            if (hit) {
                hitCount++;
                sumMrr += 1.0 / rank;
            } else {
                failures.add(query + " → 未命中（期望 " + expected + "，返回 "
                        + results.size() + " 条）");
            }
            assertThat(hit || minHitAtK == 0)
                    .as("查询 [%s] 未命中期望文档", query)
                    .isTrue();
        }

        double hitRate = (double) hitCount / cases.size();
        double mrr = sumMrr / cases.size();
        double minHitRate = Double.parseDouble(System.getenv().getOrDefault("LUMINA_RAG_MIN_HIT_RATE", "0.8"));
        double minMrr = Double.parseDouble(System.getenv().getOrDefault("LUMINA_RAG_MIN_MRR", "0.6"));

        // 汇总指标不达标时整体判失败，附逐 case 明细
        assertThat(hitRate)
                .as("golden set hit@k = %.3f（阈值 %.2f，%s，共 %d 例）%n失败明细:%n%s",
                        hitRate, minHitRate, goldenSetSource, cases.size(), String.join("\n", failures))
                .isGreaterThanOrEqualTo(minHitRate);
        assertThat(mrr)
                .as("golden set MRR = %.3f（阈值 %.2f）", mrr, minMrr)
                .isGreaterThanOrEqualTo(minMrr);
    }

    /** 返回首个期望文档的排名（1 起）；未命中返回 0 */
    private int findFirstExpectedRank(List<Map<String, Object>> results, List<String> expected) {
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> item = results.get(i);
            Object meta = item.get("metadata");
            String docUuid = meta instanceof Map ? String.valueOf(((Map<?, ?>) meta).get("docUuid")) : "";
            for (String want : expected) {
                if (want.equals(docUuid)) {
                    return i + 1;
                }
                // 容错匹配：golden set 也可标注内容片段（分块无 docUuid 时）
                String content = String.valueOf(item.getOrDefault("content", ""));
                if (!want.equals("") && content.contains(want)) {
                    return i + 1;
                }
            }
        }
        return 0;
    }
}
