package io.lumina.agent.loader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 提示词加载器
 *
 * <p>从资源文件加载提示词模板。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class PromptLoader {

    /**
     * 加载提示词模板
     *
     * @param promptName 提示词名称
     * @return 提示词内容
     */
    public String loadPrompt(String promptName) {
        String promptPath = String.format("prompts/%s.txt", promptName);
        try {
            ClassPathResource resource = new ClassPathResource(promptPath);
            if (!resource.exists()) {
                log.warn("提示词文件不存在: {}，使用默认提示词", promptPath);
                return getDefaultPrompt(promptName);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                String prompt = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                log.info("加载提示词成功: {}", promptName);
                return prompt;
            }
        } catch (IOException e) {
            log.error("加载提示词失败: {}", promptName, e);
            return getDefaultPrompt(promptName);
        }
    }

    /**
     * 填充提示词模板
     *
     * @param template 提示词模板
     * @param params   参数
     * @return 填充后的提示词
     */
    public String fillTemplate(String template, Object... params) {
        if (template == null) {
            return "";
        }
        String result = template;
        for (int i = 0; i < params.length; i++) {
            result = result.replace("{" + i + "}", params[i] != null ? params[i].toString() : "");
        }
        return result;
    }

    /**
     * 获取默认提示词
     */
    private String getDefaultPrompt(String promptName) {
        return String.format("你是一个专业的 %s 助手，请根据用户需求提供帮助。", promptName);
    }
}
