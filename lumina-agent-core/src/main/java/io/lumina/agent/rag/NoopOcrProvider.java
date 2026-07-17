package io.lumina.agent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 空 OCR Provider（默认实现，不调任何服务）
 *
 * <p>当 lumina.rag.reader.ocr.provider 未配置或为 none 时激活。
 * recognize 返回空字符串，上层逻辑会走"暂不支持 OCR"的提示路径。
 *
 * <p>使用 {@code @ConditionalOnProperty(matchIfMissing=true)} 而非
 * {@code @ConditionalOnMissingBean}，确保 Bean 选择确定性不受扫描顺序影响。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.rag.reader.ocr", name = "provider",
        havingValue = "none", matchIfMissing = true)
public class NoopOcrProvider implements OcrProvider {

    @Override
    public String recognize(byte[] imageBytes, String language) {
        log.debug("OCR 未启用（provider=none），跳过识别");
        return "";
    }

    @Override
    public String getName() {
        return "none";
    }
}
