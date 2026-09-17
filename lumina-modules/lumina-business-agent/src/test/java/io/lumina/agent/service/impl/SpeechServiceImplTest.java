package io.lumina.agent.service.impl;

import io.lumina.agent.api.dto.llm.QueryLlmProviderDTO;
import io.lumina.agent.api.vo.LlmProviderVO;
import io.lumina.agent.service.LlmProviderService;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SpeechServiceImpl 单元测试（密钥解析顺序、格式映射、入参校验；SDK 调用路径需真实密钥不做单测）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
class SpeechServiceImplTest {

    private final LlmProviderService llmProviderService = Mockito.mock(LlmProviderService.class);

    private final SpeechServiceImpl service = new SpeechServiceImpl(llmProviderService);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "speechApiKey", "");
        ReflectionTestUtils.setField(service, "llmDefaultApiKey", "");
        ReflectionTestUtils.setField(service, "sttModel", "paraformer-realtime-v2");
        ReflectionTestUtils.setField(service, "ttsModel", "cosyvoice-v2");
        ReflectionTestUtils.setField(service, "defaultVoice", "longxiaochun");
    }

    @Test
    void resolveApiKeyPrefersDedicatedProperty() {
        ReflectionTestUtils.setField(service, "speechApiKey", "sk-speech");

        assertThat(service.resolveApiKey()).isEqualTo("sk-speech");
        Mockito.verifyNoInteractions(llmProviderService);
    }

    @Test
    void resolveApiKeyFallsBackToDashScopeProvider() {
        LlmProviderVO provider = new LlmProviderVO();
        provider.setId(9L);
        provider.setProvider("DASHSCOPE");
        provider.setStatus(1);
        provider.setHasApiKey(true);
        Mockito.when(llmProviderService.list(Mockito.any(QueryLlmProviderDTO.class)))
                .thenReturn(List.of(provider));
        Mockito.when(llmProviderService.getDecryptedApiKey(9L)).thenReturn("sk-provider");

        assertThat(service.resolveApiKey()).isEqualTo("sk-provider");
    }

    @Test
    void resolveApiKeyFallsBackToLlmDefault() {
        Mockito.when(llmProviderService.list(Mockito.any(QueryLlmProviderDTO.class)))
                .thenReturn(List.of());
        ReflectionTestUtils.setField(service, "llmDefaultApiKey", "sk-llm");

        assertThat(service.resolveApiKey()).isEqualTo("sk-llm");
    }

    @Test
    void resolveApiKeyThrowsWhenMissing() {
        Mockito.when(llmProviderService.list(Mockito.any(QueryLlmProviderDTO.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.resolveApiKey())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key");
    }

    @Test
    void audioFormatMapsByExtension() {
        assertThat(SpeechServiceImpl.audioFormatOf("voice.webm")).isEqualTo("opus");
        assertThat(SpeechServiceImpl.audioFormatOf("voice.mp3")).isEqualTo("mp3");
        assertThat(SpeechServiceImpl.audioFormatOf("voice.m4a")).isEqualTo("aac");
        assertThat(SpeechServiceImpl.audioFormatOf("voice.wav")).isEqualTo("wav");
        assertThat(SpeechServiceImpl.audioFormatOf(null)).isEqualTo("wav");
    }

    @Test
    void transcribeRejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.wav", "audio/wav", new byte[0]);

        assertThatThrownBy(() -> service.transcribe(empty))
                .hasMessageContaining("为空");
    }

    @Test
    void transcribeRejectsOversizedFile() {
        MockMultipartFile big = new MockMultipartFile("file", "a.wav", "audio/wav", new byte[10]) {
            @Override
            public long getSize() {
                return SpeechServiceImpl.MAX_AUDIO_BYTES + 1;
            }
        };

        assertThatThrownBy(() -> service.transcribe(big))
                .hasMessageContaining("10MB");
    }

    @Test
    void synthesizeValidatesText() {
        assertThatThrownBy(() -> service.synthesize("  ", null)).hasMessageContaining("不能为空");
        assertThatThrownBy(() -> service.synthesize("a".repeat(SpeechServiceImpl.MAX_TTS_CHARS + 1), null))
                .hasMessageContaining("2000");
    }
}
