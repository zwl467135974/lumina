package io.lumina.agent.service.impl;

import io.lumina.agent.api.vo.LlmProviderVO;
import io.lumina.agent.service.LlmProviderService;
import io.lumina.agent.service.SpeechService;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 语音服务实现
 *
 * <p>STT：paraformer 实时识别以"整段喂入"方式转写录音文件（单次 ≤ 10MB、
 * 约 60s，满足会话语音输入）；按句终点去重拼接（实时协议会先给增量后定稿）。
 *
 * <p>TTS：cosyvoice 非流式合成，输出 WAV_16000HZ_MONO_16BIT（浏览器
 * Audio 标签可直接播放）。
 *
 * <p>密钥解析顺序：lumina.speech.api-key → 当前租户 Provider 表中启用的
 * DASHSCOPE Provider → lumina.agent.llm.api-key 兜底。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Service
public class SpeechServiceImpl implements SpeechService {

    /** STT 单文件上限（paraformer 实时接口约 60s 音频） */
    static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    /** TTS 单次合成文本上限（cosyvoice 限制） */
    static final int MAX_TTS_CHARS = 2000;

    /** 实时识别喂入分片大小（16k 16bit 单声道 ≈ 500ms） */
    private static final int AUDIO_CHUNK_BYTES = 16000;

    @Value("${lumina.speech.api-key:}")
    private String speechApiKey;

    @Value("${lumina.agent.llm.api-key:}")
    private String llmDefaultApiKey;

    @Value("${lumina.speech.stt.model:paraformer-realtime-v2}")
    private String sttModel;

    @Value("${lumina.speech.tts.model:cosyvoice-v2}")
    private String ttsModel;

    @Value("${lumina.speech.tts.voice:longxiaochun}")
    private String defaultVoice;

    private final LlmProviderService llmProviderService;

    public SpeechServiceImpl(LlmProviderService llmProviderService) {
        this.llmProviderService = llmProviderService;
    }

    @Override
    public String transcribe(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "音频文件为空");
        }
        if (audioFile.getSize() > MAX_AUDIO_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "音频超过 10MB 上限");
        }
        try {
            byte[] audio = audioFile.getBytes();
            RecognitionParam param = RecognitionParam.builder()
                    .model(sttModel)
                    .format(audioFormatOf(audioFile.getOriginalFilename()))
                    .sampleRate(16000)
                    .apiKey(resolveApiKey())
                    .build();

            List<ByteBuffer> chunks = chunkAudio(audio);
            Recognition recognizer = new Recognition();
            List<RecognitionResult> results = recognizer
                    .streamCall(param, Flowable.fromIterable(chunks))
                    .toList()
                    .blockingGet();

            StringBuilder text = new StringBuilder();
            for (RecognitionResult result : results) {
                if (result.isSentenceEnd() && result.getSentence() != null
                        && result.getSentence().getText() != null
                        && !result.getSentence().getText().isBlank()) {
                    text.append(result.getSentence().getText());
                }
            }
            String transcript = text.toString().trim();
            log.info("语音转写完成: file={}, bytes={}, chars={}",
                    audioFile.getOriginalFilename(), audio.length, transcript.length());
            return transcript;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("语音转写失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "语音转写失败: " + e.getMessage());
        }
    }

    @Override
    public byte[] synthesize(String text, String voice) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "合成文本不能为空");
        }
        if (text.length() > MAX_TTS_CHARS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "合成文本超过 " + MAX_TTS_CHARS + " 字符上限");
        }
        String apiKey = resolveApiKey();
        try {
            SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                    .model(ttsModel)
                    .voice(voice != null && !voice.isBlank() ? voice.trim() : defaultVoice)
                    .format(SpeechSynthesisAudioFormat.WAV_16000HZ_MONO_16BIT)
                    .apiKey(apiKey)
                    .build();

            ByteArrayOutputStream collected = new ByteArrayOutputStream();
            Exception[] callbackError = new Exception[1];
            SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, new ResultCallback<SpeechSynthesisResult>() {
                @Override
                public void onEvent(SpeechSynthesisResult result) {
                    ByteBuffer frame = result.getAudioFrame();
                    if (frame != null && frame.hasRemaining()) {
                        byte[] bytes = new byte[frame.remaining()];
                        frame.get(bytes);
                        collected.write(bytes, 0, bytes.length);
                    }
                }

                @Override
                public void onComplete() {
                }

                @Override
                public void onError(Exception e) {
                    callbackError[0] = e;
                }
            });
            ByteBuffer audio = synthesizer.call(text.trim());

            byte[] body;
            if (audio != null && audio.hasRemaining()) {
                body = new byte[audio.remaining()];
                audio.get(body);
            } else if (callbackError[0] != null) {
                throw callbackError[0];
            } else {
                body = collected.toByteArray();
            }
            if (body.length == 0) {
                throw new IllegalStateException("合成结果为空");
            }
            log.info("语音合成完成: chars={}, bytes={}, voice={}", text.length(), body.length, param.getVoice());
            return body;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("语音合成失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "语音合成失败: " + e.getMessage());
        }
    }

    /**
     * 密钥解析：专用配置 → 租户 DASHSCOPE Provider → LLM 兜底配置
     */
    String resolveApiKey() {
        if (speechApiKey != null && !speechApiKey.isBlank()) {
            return speechApiKey;
        }
        try {
            io.lumina.agent.api.dto.llm.QueryLlmProviderDTO query = new io.lumina.agent.api.dto.llm.QueryLlmProviderDTO();
            query.setProvider("DASHSCOPE");
            query.setStatus(1);
            for (LlmProviderVO provider : llmProviderService.list(query)) {
                if (Boolean.TRUE.equals(provider.getHasApiKey())) {
                    String key = llmProviderService.getDecryptedApiKey(provider.getId());
                    if (key != null && !key.isBlank()) {
                        return key;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("从 Provider 表解析语音密钥失败，回退默认配置: {}", e.getMessage());
        }
        if (llmDefaultApiKey != null && !llmDefaultApiKey.isBlank()) {
            return llmDefaultApiKey;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST,
                "语音功能未配置 DashScope API Key（lumina.speech.api-key 或 DASHSCOPE 类型 Provider）");
    }

    /** 文件扩展名 → paraformer 支持的格式串（未知按 wav 处理，前端统一转 WAV） */
    static String audioFormatOf(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3")) {
            return "mp3";
        }
        if (lower.endsWith(".opus") || lower.endsWith(".webm")) {
            return "opus";
        }
        if (lower.endsWith(".aac") || lower.endsWith(".m4a")) {
            return "aac";
        }
        return "wav";
    }

    private static List<ByteBuffer> chunkAudio(byte[] audio) {
        List<ByteBuffer> chunks = new ArrayList<>();
        for (int offset = 0; offset < audio.length; offset += AUDIO_CHUNK_BYTES) {
            int end = Math.min(offset + AUDIO_CHUNK_BYTES, audio.length);
            chunks.add(ByteBuffer.wrap(audio, offset, end - offset));
        }
        if (chunks.isEmpty()) {
            chunks.add(ByteBuffer.wrap(audio));
        }
        return chunks;
    }
}
