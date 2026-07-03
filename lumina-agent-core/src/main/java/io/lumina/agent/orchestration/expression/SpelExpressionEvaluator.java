package io.lumina.agent.orchestration.expression;

import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Spring Expression Language (SpEL) 表达式引擎实现
 *
 * <p>使用 Spring 原生 SpEL 引擎，支持方法调用、类型引用、集合操作等。
 *
 * <p>表达式中以 {@code #变量名} 引用上下文变量，如：
 * <ul>
 *   <li>{@code #category == 'refund'} — 条件判断</li>
 *   <li>{@code #result.toUpperCase()} — 方法调用</li>
 *   <li>{@code T(JSON).parse(#result)} — 类型引用</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Component
public class SpelExpressionEvaluator implements ExpressionEvaluator {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Override
    public Object evaluate(String expression, Map<String, Object> variables) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        if (variables != null) {
            variables.forEach(ctx::setVariable);
        }
        return parser.parseExpression(expression).getValue(ctx);
    }
}
