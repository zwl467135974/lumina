package io.lumina.agent.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ProviderPresets 单元测试
 *
 * <p>覆盖预设 Provider 判定、Base URL 映射、边界值。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class ProviderPresetsTest {

    @Test
    void knownProvidersAreCompatible() {
        assertThat(ProviderPresets.isOpenAICompatible("glm")).isTrue();
        assertThat(ProviderPresets.isOpenAICompatible("kimi")).isTrue();
        assertThat(ProviderPresets.isOpenAICompatible("doubao")).isTrue();
        assertThat(ProviderPresets.isOpenAICompatible("minimax")).isTrue();
        assertThat(ProviderPresets.isOpenAICompatible("deepseek")).isTrue();
        assertThat(ProviderPresets.isOpenAICompatible("yi")).isTrue();
        assertThat(ProviderPresets.isOpenAICompatible("qwen")).isTrue();
    }

    @Test
    void unknownProvidersNotCompatible() {
        assertThat(ProviderPresets.isOpenAICompatible("dashscope")).isFalse();
        assertThat(ProviderPresets.isOpenAICompatible("openai")).isFalse();
        assertThat(ProviderPresets.isOpenAICompatible("anthropic")).isFalse();
        assertThat(ProviderPresets.isOpenAICompatible("ollama")).isFalse();
        assertThat(ProviderPresets.isOpenAICompatible("nonexistent")).isFalse();
    }

    @Test
    void getPresetBaseUrlReturnsCorrectUrl() {
        assertThat(ProviderPresets.getPresetBaseUrl("glm"))
                .isEqualTo("https://open.bigmodel.cn/api/paas/v4");
        assertThat(ProviderPresets.getPresetBaseUrl("kimi"))
                .isEqualTo("https://api.moonshot.cn/v1");
        assertThat(ProviderPresets.getPresetBaseUrl("doubao"))
                .isEqualTo("https://ark.cn-beijing.volces.com/api/v3");
        assertThat(ProviderPresets.getPresetBaseUrl("minimax"))
                .isEqualTo("https://api.minimax.chat/v1");
        assertThat(ProviderPresets.getPresetBaseUrl("deepseek"))
                .isEqualTo("https://api.deepseek.com/v1");
        assertThat(ProviderPresets.getPresetBaseUrl("yi"))
                .isEqualTo("https://api.lingyiwanwu.com/v1");
        assertThat(ProviderPresets.getPresetBaseUrl("qwen"))
                .isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1");
    }

    @Test
    void getPresetBaseUrlReturnsNullForUnknown() {
        assertThat(ProviderPresets.getPresetBaseUrl("unknown")).isNull();
    }

    @Test
    void presetsMapIsImmutable() {
        assertThatThrownBy(() -> ProviderPresets.OPENAI_COMPATIBLE_PRESETS.put("hack", "url"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
