package io.lumina.base.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 *
 * <p>用于标注需要特定权限才能访问的接口或方法。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 需要的权限编码
     *
     * <p>支持多个权限，只要拥有其中一个即可访问。
     *
     * @return 权限编码数组
     */
    String[] value() default {};

    /**
     * 权限逻辑关系
     *
     * <p>true-需要拥有所有权限（AND），false-拥有任一权限即可（OR）
     *
     * @return 逻辑关系
     */
    boolean requireAll() default false;
}
