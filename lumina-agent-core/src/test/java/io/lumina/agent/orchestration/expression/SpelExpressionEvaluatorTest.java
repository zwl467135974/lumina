package io.lumina.agent.orchestration.expression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SpelExpressionEvaluator 单元测试
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class SpelExpressionEvaluatorTest {

    private SpelExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpelExpressionEvaluator();
    }

    @Test
    void evaluateSimpleVariable() {
        Map<String, Object> vars = Map.of("name", "Lumina");
        Object result = evaluator.evaluate("#name", vars);
        assertThat(result).isEqualTo("Lumina");
    }

    @Test
    void evaluateStringComparison() {
        Map<String, Object> vars = Map.of("category", "refund");
        Object result = evaluator.evaluate("#category == 'refund'", vars);
        assertThat(result).isEqualTo(true);
    }

    @Test
    void evaluateStringConcatenation() {
        Map<String, Object> vars = Map.of("a", "Hello", "b", "World");
        Object result = evaluator.evaluate("#a + ' ' + #b", vars);
        assertThat(result).isEqualTo("Hello World");
    }

    @Test
    void evaluateMethod() {
        Map<String, Object> vars = Map.of("text", "hello world");
        Object result = evaluator.evaluate("#text.toUpperCase()", vars);
        assertThat(result).isEqualTo("HELLO WORLD");
    }

    @Test
    void evaluateBooleanType() {
        Map<String, Object> vars = Map.of("flag", "true");
        Boolean result = evaluator.evaluate("#flag", vars, Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void evaluateBooleanShortcut() {
        assertThat(evaluator.evaluateBoolean("true", Map.of())).isTrue();
        assertThat(evaluator.evaluateBoolean("false", Map.of())).isFalse();
    }

    @Test
    void evaluateBooleanNullExpressionReturnsTrue() {
        assertThat(evaluator.evaluateBoolean(null, Map.of())).isTrue();
        assertThat(evaluator.evaluateBoolean("", Map.of())).isTrue();
        assertThat(evaluator.evaluateBoolean("  ", Map.of())).isTrue();
    }

    @Test
    void evaluateContains() {
        Map<String, Object> vars = Map.of("text", "error: something went wrong");
        Object result = evaluator.evaluate("#text.contains('error')", vars);
        assertThat(result).isEqualTo(true);
    }

    @Test
    void evaluateLength() {
        Map<String, Object> vars = Map.of("text", "hello");
        Object result = evaluator.evaluate("#text.length()", vars);
        assertThat(result).isEqualTo(5);
    }

    @Test
    void evaluateTernary() {
        Map<String, Object> vars = Map.of("status", "active");
        Object result = evaluator.evaluate("#status == 'active' ? 'OK' : 'INACTIVE'", vars);
        assertThat(result).isEqualTo("OK");
    }
}
