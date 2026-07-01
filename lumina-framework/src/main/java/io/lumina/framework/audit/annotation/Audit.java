package io.lumina.framework.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解
 *
 * <p>标注在 Controller 方法上，由 {@code AuditAspect} 切面拦截，
 * 自动记录操作人、模块、动作、目标、耗时与执行结果，异步落审计表。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audit {

    /**
     * 业务模块（auth/user/role/permission/tenant/agent/conversation）
     */
    String module();

    /**
     * 操作类型（CREATE/UPDATE/DELETE/EXECUTE/LOGIN/LOGOUT/EXPORT）
     */
    String action();

    /**
     * 描述（可选，补充说明）
     */
    String description() default "";
}
