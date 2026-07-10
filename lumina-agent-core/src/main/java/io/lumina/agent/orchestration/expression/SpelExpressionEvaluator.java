package io.lumina.agent.orchestration.expression;

import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.TypeLocator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Spring Expression Language (SpEL) 表达式引擎实现
 *
 * <p>使用 {@link StandardEvaluationContext} 配合自定义 {@link TypeLocator}，
 * 允许方法调用（如 {@code #str.contains('x')}）但禁止类型引用（{@code T(...)}），
 * 防止表达式注入 RCE。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Component
public class SpelExpressionEvaluator implements ExpressionEvaluator {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    private static final TypeLocator BLOCKING_TYPE_LOCATOR = typeName -> {
        throw new IllegalArgumentException("Type reference not allowed: " + typeName);
    };

    @Override
    public Object evaluate(String expression, Map<String, Object> variables) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setTypeLocator(BLOCKING_TYPE_LOCATOR);
        if (variables != null) {
            variables.forEach(ctx::setVariable);
        }
        return parser.parseExpression(expression).getValue(ctx);
    }
}
