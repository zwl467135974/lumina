package io.lumina.base.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解
 *
 * <p>用于标注需要特定角色才能访问的接口或方法。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 需要的角色编码
     *
     * <p>支持多个角色，只要拥有其中一个角色即可访问。
     *
     * @return 角色编码数组
     */
    String[] value() default {};

    /**
     * 角色逻辑关系
     *
     * <p>true-需要拥有所有角色（AND），false-拥有任一角色即可（OR）
     *
     * @return 逻辑关系
     */
    boolean requireAll() default false;
}
