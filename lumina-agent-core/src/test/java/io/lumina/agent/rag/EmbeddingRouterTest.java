package io.lumina.agent.rag;

import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.lumina.agent.config.RagProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EmbeddingRouter 单元测试
 *
 * @author Lumina Team
 * @since 2.1.0
 */
class EmbeddingRouterTest {

    @Test
    void routesChineseTextToZhModel() {
        Map<String, EmbeddingModel> models = createModels();
        EmbeddingRouter router = new EmbeddingRouter(models, "default", "language");
        TrackingModel zhModel = (TrackingModel) models.get("zh");

        router.embed(TextBlock.builder().text("这是一个中文文档，用于测试语言检测路由").build()).block();

        assertThat(zhModel.lastText).contains("中文文档");
    }

    @Test
    void routesEnglishTextToEnModel() {
        Map<String, EmbeddingModel> models = createModels();
        EmbeddingRouter router = new EmbeddingRouter(models, "default", "language");
        TrackingModel enModel = (TrackingModel) models.get("en");

        router.embed(TextBlock.builder().text("This is an English document for testing language routing").build()).block();

        assertThat(enModel.lastText).contains("English");
    }

    @Test
    void fallsBackToDefaultWhenNoMatch() {
        Map<String, EmbeddingModel> models = new LinkedHashMap<>();
        models.put("default", new TrackingModel("default", 1024));
        EmbeddingRouter router = new EmbeddingRouter(models, "default", "language");
        TrackingModel defaultModel = (TrackingModel) models.get("default");

        router.embed(TextBlock.builder().text("12345").build()).block();

        assertThat(defaultModel.callCount).isEqualTo(1);
    }

    @Test
    void returnsDimensionsOfDefaultModel() {
        Map<String, EmbeddingModel> models = createModels();
        EmbeddingRouter router = new EmbeddingRouter(models, "default", "language");

        assertThat(router.getDimensions()).isEqualTo(1024);
        assertThat(router.getModelName()).contains("router");
    }

    @Test
    void isChineseTextDetectsCJKCorrectly() {
        assertThat(EmbeddingRouter.isChineseText("你好世界，这是一个测试")).isTrue();
        assertThat(EmbeddingRouter.isChineseText("Hello World, this is a test")).isFalse();
        assertThat(EmbeddingRouter.isChineseText("")).isFalse();
        assertThat(EmbeddingRouter.isChineseText("这是一段中文内容包含少量 English 单词")).isTrue();
    }

    private Map<String, EmbeddingModel> createModels() {
        Map<String, EmbeddingModel> models = new LinkedHashMap<>();
        models.put("default", new TrackingModel("default", 1024));
        models.put("zh", new TrackingModel("dashscope", 1024));
        models.put("en", new TrackingModel("openai", 1024));
        return models;
    }

    /**
     * 测试用 EmbeddingModel 实现，记录最后一次嵌入的文本
     */
    static class TrackingModel implements EmbeddingModel {
        private final String name;
        private final int dims;
        String lastText;
        int callCount = 0;

        TrackingModel(String name, int dims) {
            this.name = name;
            this.dims = dims;
        }

        @Override
        public Mono<double[]> embed(ContentBlock content) {
            callCount++;
            if (content instanceof TextBlock tb) {
                lastText = tb.getText();
            }
            return Mono.just(new double[dims]);
        }

        @Override
        public String getModelName() { return name; }

        @Override
        public int getDimensions() { return dims; }
    }
}
