package io.lumina.framework.audit.aspect;

import io.lumina.common.core.BaseContext;
import io.lumina.framework.audit.annotation.Audit;
import io.lumina.framework.audit.event.AuditEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * 审计日志切面
 *
 * <p>拦截标注 {@link Audit} 的方法，记录执行结果并发布 {@link AuditEvent}，
 * 由审计监听器异步持久化。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint pjp, Audit audit) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        String errorMsg = null;
        Object result = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable e) {
            success = false;
            errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - start;
            try {
                publishAuditEvent(pjp, audit, result, success, errorMsg, duration);
            } catch (Exception e) {
                log.warn("发布审计事件失败", e);
            }
        }
    }

    /**
     * 构建并发布审计事件
     */
    private void publishAuditEvent(ProceedingJoinPoint pjp, Audit audit, Object result,
                                   boolean success, String errorMsg, long duration) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();

        // 提取目标信息（方法名作为 targetType，参数或返回值的 ID 作为 targetId）
        String targetType = method.getDeclaringClass().getSimpleName();
        String targetId = extractTargetId(method, pjp.getArgs(), result);

        HttpServletRequest request = getCurrentRequest();

        AuditEvent event = AuditEvent.builder()
                .module(audit.module())
                .action(audit.action())
                .description(audit.description())
                .targetType(targetType)
                .targetId(targetId)
                .success(success)
                .errorMsg(errorMsg)
                .durationMs(duration)
                .tenantId(BaseContext.getTenantId())
                .userId(BaseContext.getUserId())
                .username(BaseContext.getUsername())
                .requestMethod(request != null ? request.getMethod() : null)
                .requestUrl(request != null ? request.getRequestURI() : null)
                .requestIp(request != null ? getClientIp(request) : null)
                .build();

        eventPublisher.publishEvent(event);
    }

    /**
     * 从方法参数或返回值中提取目标 ID（启发式：id/agentId/uuid 参数或返回值字段）
     */
    private String extractTargetId(Method method, Object[] args, Object result) {
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length && i < args.length; i++) {
            String name = params[i].getName().toLowerCase();
            if (name.contains("id") && args[i] != null) {
                return String.valueOf(args[i]);
            }
            if (name.contains("uuid") && args[i] != null) {
                return String.valueOf(args[i]);
            }
        }
        // 尝试从返回值提取 id 字段（反射）
        if (result != null) {
            try {
                java.lang.reflect.Field idField = findField(result.getClass(), "id");
                if (idField == null) {
                    idField = findField(result.getClass(), "agentId");
                }
                if (idField != null) {
                    idField.setAccessible(true);
                    Object idVal = idField.get(result);
                    if (idVal != null) {
                        return String.valueOf(idVal);
                    }
                }
            } catch (Exception ignored) {
                // 忽略反射异常
            }
        }
        return null;
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 获取当前 HTTP 请求（Web MVC 场景）
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
