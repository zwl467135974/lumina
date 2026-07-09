package io.lumina.agent.orchestration.expression;

import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Spring Expression Language (SpEL) 表达式引擎实现
 *
 * <p>使用 {@link SimpleEvaluationContext#forReadOnlyDataBinding()}（只读数据绑定模式），
 * 禁止类型引用（{@code T(...)}）和方法调用，防止表达式注入 RCE。
 *
 * <p>表达式中以 {@code #变量名} 引用上下文变量，如：
 * <ul>
 *   <li>{@code #category == 'refund'} — 条件判断</li>
 *   <li>{@code #list[0]} — 集合索引</li>
 *   <li>{@code #obj.name} — 属性访问</li>
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
        SimpleEvaluationContext ctx = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        if (variables != null) {
            variables.forEach(ctx::setVariable);
        }
        return parser.parseExpression(expression).getValue(ctx);
    }
}
