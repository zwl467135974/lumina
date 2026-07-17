package io.lumina.standalone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 单体模式白名单配置
 *
 * <p>复制自 lumina-gateway 的 WhitelistConfig（standalone 不能依赖 gateway 模块——
 * gateway 是 WebFlux 技术栈），配置前缀保持 {@code lumina.whitelist} 不变。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "lumina.whitelist")
public class WhitelistConfig {

    /**
     * 白名单路径列表
     */
    private List<String> paths = new ArrayList<>();

    /**
     * 检查路径是否在白名单中
     *
     * @param path 请求路径
     * @return 是否在白名单中
     */
    public boolean isWhitelisted(String path) {
        return paths.stream().anyMatch(whitePath -> {
            // 精确匹配
            if (path.equals(whitePath)) {
                return true;
            }
            // 前缀匹配（支持通配符路径）
            if (whitePath.endsWith("/**")) {
                String prefix = whitePath.substring(0, whitePath.length() - 3);
                return path.startsWith(prefix);
            }
            // 路径前缀匹配（带斜杠，防止 /api/login 匹配 /api/loginInfo）
            if (path.startsWith(whitePath + "/")) {
                return true;
            }
            return false;
        });
    }
}
