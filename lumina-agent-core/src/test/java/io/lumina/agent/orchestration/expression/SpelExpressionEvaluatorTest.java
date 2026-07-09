package io.lumina.agent.orchestration.expression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void evaluateCollectionIndex() {
        Map<String, Object> vars = Map.of("list", List.of("a", "b", "c"));
        Object result = evaluator.evaluate("#list[1]", vars);
        assertThat(result).isEqualTo("b");
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
    void evaluateTypeReferenceBlocked() {
        Map<String, Object> vars = Map.of("name", "test");
        assertThatThrownBy(() -> evaluator.evaluate("T(java.lang.Runtime)", vars))
                .isInstanceOf(Exception.class);
    }

    @Test
    void evaluateMapPropertyAccess() {
        Map<String, Object> vars = Map.of("user", Map.of("name", "Alice"));
        Object result = evaluator.evaluate("#user['name']", vars);
        assertThat(result).isEqualTo("Alice");
    }

    @Test
    void evaluateTernary() {
        Map<String, Object> vars = Map.of("status", "active");
        Object result = evaluator.evaluate("#status == 'active' ? 'OK' : 'INACTIVE'", vars);
        assertThat(result).isEqualTo("OK");
    }
}
