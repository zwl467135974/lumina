package io.lumina.agent.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 语音服务（DashScope paraformer STT + cosyvoice TTS）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
public interface SpeechService {

    /**
     * 语音转文本（paraformer 实时识别，整段音频一次转写）
     *
     * @param audioFile 音频文件（wav 16k 单声道最佳；支持 mp3/opus/aac）
     * @return 识别文本
     */
    String transcribe(MultipartFile audioFile);

    /**
     * 文本转语音（cosyvoice，返回 16k 单声道 16bit WAV，浏览器可直接播放）
     *
     * @param text  合成文本（≤2000 字符）
     * @param voice 音色（空则用默认配置，如 longxiaochun）
     */
    byte[] synthesize(String text, String voice);
}
