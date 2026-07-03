package io.lumina.agent.orchestration.expression;

import java.util.Map;

/**
 * 表达式求值接口
 *
 * <p>用于工作流中的条件判断、数据提取、变量引用等。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface ExpressionEvaluator {

    /**
     * 求值表达式
     *
     * @param expression 表达式字符串（如 {@code #category == 'refund'}）
     * @param variables  变量空间
     * @return 求值结果
     */
    Object evaluate(String expression, Map<String, Object> variables);

    /**
     * 求值表达式并按指定类型返回
     */
    @SuppressWarnings("unchecked")
    default <T> T evaluate(String expression, Map<String, Object> variables, Class<T> expectedType) {
        Object result = evaluate(expression, variables);
        if (result == null) {
            return null;
        }
        if (expectedType.isInstance(result)) {
            return (T) result;
        }
        if (expectedType == String.class) {
            return (T) String.valueOf(result);
        }
        if (expectedType == Boolean.class || expectedType == boolean.class) {
            if (result instanceof Boolean b) return (T) b;
            return (T) Boolean.valueOf(String.valueOf(result));
        }
        if (expectedType == Integer.class || expectedType == int.class) {
            if (result instanceof Number n) return (T) (Integer) n.intValue();
            return (T) Integer.valueOf(String.valueOf(result));
        }
        return (T) result;
    }

    /**
     * 求值布尔表达式
     */
    default boolean evaluateBoolean(String expression, Map<String, Object> variables) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        Boolean result = evaluate(expression, variables, Boolean.class);
        return Boolean.TRUE.equals(result);
    }
}
