package io.lumina.agent.evaluation.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import java.util.Map;

/**
 * 单条测试用例
 *
 * <p>YAML 中可用 {@code caseId} 或 {@code id} 作为标识字段名。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class TestCase {
    @JsonAlias("caseId")
    private String id;
    private String input;
    private String expected;
    private String category;
    private Map<String, String> tags;
}
