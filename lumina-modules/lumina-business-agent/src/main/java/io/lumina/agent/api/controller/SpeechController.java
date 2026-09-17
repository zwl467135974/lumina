package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.TtsRequestDTO;
import io.lumina.agent.service.SpeechService;
import io.lumina.common.annotation.RequirePermission;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 语音 API（会话语音输入 + 回复朗读）
 *
 * <p>挂在 /api/v1/agents 前缀下，复用既有网关路由与 agent:list 权限。
 * STT 返回文本；TTS 返回 16k 单声道 WAV（浏览器 Audio 直接播放）。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Tag(name = "语音", description = "语音识别（paraformer）与语音合成（cosyvoice）")
@RestController
@RequirePermission("agent:list")
@RequestMapping("/api/v1/agents/speech")
@RequiredArgsConstructor
@Validated
public class SpeechController {

    private final SpeechService speechService;

    @Audit(module = "speech", action = "EXECUTE", description = "语音转文本")
    @Operation(summary = "语音转文本（wav 16k 最佳，支持 mp3/opus/aac，≤10MB）")
    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<String> transcribe(@RequestParam("file") MultipartFile file) {
        return R.success(speechService.transcribe(file));
    }

    @Audit(module = "speech", action = "EXECUTE", description = "文本转语音")
    @Operation(summary = "文本转语音（返回 WAV 音频）")
    @PostMapping("/tts")
    public ResponseEntity<byte[]> tts(@Valid @RequestBody TtsRequestDTO dto) {
        byte[] audio = speechService.synthesize(dto.getText(), dto.getVoice());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=speech.wav")
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(audio);
    }
}
