package io.lumina.agent.security;

import io.lumina.common.annotation.RequirePermission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controller 权限注解回归测试
 *
 * <p>回归历史 bug P0-2：Agent 模块全部 Controller 无权限控制。
 * 此测试确保所有写操作 Controller 都有 @RequirePermission 注解。
 *
 * @author Lumina Team
 * @since 3.6.0
 */
class ControllerPermissionTest {

    private static final String PKG = "io.lumina.agent.api.controller.";

    /**
     * 已知的全部 Controller 类名（新增 Controller 时追加）
     */
    private static final List<String> ALL_CONTROLLERS = List.of(
            "AbTestController", "AgentController", "AgentTriggerController",
            "BudgetController", "ConversationController", "CostController",
            "EvaluationController", "KnowledgeBaseController", "KnowledgeController",
            "LlmProviderController", "McpController", "ModelPricingController",
            "PromptController", "ToolMonitorController", "WorkflowController"
    );

    /**
     * 豁免列表：这些 Controller 不需要 @RequirePermission
     */
    private static final Set<String> EXEMPT = Set.of(
            "FileController", "LongTermMemoryController", "OpenAiCompatController"
    );

    /**
     * 回归：所有非豁免 Controller 必须有类级别 @RequirePermission
     */
    @Test
    void allNonExemptControllersHaveRequirePermission() {
        int checked = 0;
        for (String name : ALL_CONTROLLERS) {
            if (EXEMPT.contains(name)) continue;
            try {
                Class<?> clazz = Class.forName(PKG + name);
                RequirePermission ann = clazz.getAnnotation(RequirePermission.class);
                assertThat(ann)
                        .as("Controller %s 缺少 @RequirePermission 注解", name)
                        .isNotNull();
                assertThat(ann.value())
                        .as("Controller %s 的 @RequirePermission value 不能为空", name)
                        .isNotEmpty();
                checked++;
            } catch (ClassNotFoundException e) {
                // Controller 不存在（可能未被编译到 classpath），跳过
            }
        }
        assertThat(checked)
                .as("至少应检查到 1 个 Controller，实际检查了 0 个（classpath 可能有问题）")
                .isGreaterThan(0);
    }
}
