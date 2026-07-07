package io.lumina.agent.evaluation.model;

import lombok.Data;
import java.util.Map;

/**
 * 单条测试用例
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class TestCase {
    private String id;
    private String input;
    private String expected;
    private String category;
    private Map<String, String> tags;
}
