package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * TTS 合成请求 DTO
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
public class TtsRequestDTO {

    @NotBlank(message = "合成文本不能为空")
    @Size(max = 2000, message = "合成文本最长 2000 字符")
    private String text;

    /** 音色（可选，空用默认；DashScope 如 longxiaochun；OpenAI 兼容如 FunAudioLLM/CosyVoice2-0.5B:alex） */
    @Size(max = 128, message = "音色名最长 128 字符")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-./:]*$", message = "音色名仅允许字母/数字/连字符/点/斜杠/冒号")
    private String voice;
}
