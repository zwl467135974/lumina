package io.lumina.agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 工具类
 *
 * <p>提供共享的 {@link ObjectMapper} 实例，避免多处 new ObjectMapper() 导致的内存浪费和配置不一致。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public final class JsonUtils {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {
    }
}
