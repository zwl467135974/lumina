package io.lumina.agent.tool;

import java.lang.annotation.*;

/**
 * Agent 工具注解
 *
 * <p>标注在方法上，表示该方法是一个 Agent 可调用的工具。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AgentTool {

    /**
     * 工具名称
     */
    String name();

    /**
     * 工具描述
     */
    String description() default "";

    /**
     * 工具分类
     */
    String category() default "default";

    /**
     * 是否启用
     */
    boolean enabled() default true;
}
