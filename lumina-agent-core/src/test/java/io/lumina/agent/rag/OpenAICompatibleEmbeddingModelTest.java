package io.lumina.agent.rag;

import io.agentscope.core.message.TextBlock;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAICompatibleEmbeddingModel 单元测试
 *
 * <p>覆盖构造器参数传递、getModelName/getDimensions 一致性、空文本异常。
 * 实际 HTTP 调用与维度校验需 E2E 验证（见端到端测试文档）。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
class OpenAICompatibleEmbeddingModelTest {

    @Test
    void constructorSetsFieldsCorrectly() {
        OpenAICompatibleEmbeddingModel model = new OpenAICompatibleEmbeddingModel(
                "sk-test", "bge-large-zh-v1.5", "https://api.siliconflow.cn/v1", 1024, false);

        assertThat(model.getModelName()).isEqualTo("bge-large-zh-v1.5");
        assertThat(model.getDimensions()).isEqualTo(1024);
    }

    @Test
    void backwardCompatConstructorDefaultsSendDimensionsFalse() {
        // 4 参数构造器应等价于 sendDimensions=false
        OpenAICompatibleEmbeddingModel model = new OpenAICompatibleEmbeddingModel(
                "sk-test", "text-embedding-3-small", "https://api.openai.com/v1", 1536);

        assertThat(model.getDimensions()).isEqualTo(1536);
        assertThat(model.getModelName()).isEqualTo("text-embedding-3-small");
    }

    @Test
    void embedNullContentThrows() {
        OpenAICompatibleEmbeddingModel model = new OpenAICompatibleEmbeddingModel(
                "sk-test", "bge-large", "https://api.example.com/v1", 1024);

        assertThatThrownBy(() -> model.embed(null).block())
                .hasMessageContaining("null");
    }

    @Test
    void embedBlankTextThrows() {
        OpenAICompatibleEmbeddingModel model = new OpenAICompatibleEmbeddingModel(
                "sk-test", "bge-large", "https://api.example.com/v1", 1024);

        TextBlock blank = TextBlock.builder().text("   ").build();
        assertThatThrownBy(() -> model.embed(blank).block())
                .hasMessageContaining("空");
    }
}
