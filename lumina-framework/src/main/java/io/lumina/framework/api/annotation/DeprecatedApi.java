package io.lumina.framework.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 废弃 API 标注
 *
 * <p>标注在 Controller 方法上，由 {@code ApiDeprecationInterceptor} 自动在响应头注入：
 * <ul>
 *   <li>{@code Deprecation: true}（IETF deprecation header）</li>
 *   <li>{@code Sunset: <date>}（RFC 8594，计划移除日期）</li>
 *   <li>{@code Link: <replacement>; rel="successor-version"}（指向新版本端点）</li>
 * </ul>
 * 前端/客户端可据此发出迁移告警。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DeprecatedApi {

    /**
     * 废弃起始版本（如 "v1.1"）
     */
    String since();

    /**
     * 替代端点路径（如 "/api/v2/users"）
     */
    String replacement() default "";

    /**
     * 计划移除日期（ISO-8601，如 "2026-12-31"）
     */
    String sunset() default "";
}
