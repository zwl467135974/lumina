package io.lumina.agent.tool.security;

import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.common.core.BaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话模式拦截器（v3.13 PLAN/BUILD/YOLO）单元测试
 *
 * @author Lumina Team
 * @since 3.13.0
 */
class SessionModeInterceptorTest {

    private LuminaAgentProperties properties;
    private SessionModeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new LuminaAgentProperties();
        properties.getTool().setSecurity(new LuminaAgentProperties.SecurityConfig());
        // 豁免开关保持关闭：PLAN 约束走纯判定，不依赖豁免开关
        properties.getTool().getSecurity().setReadonlyAutoApprove(false);
        interceptor = new SessionModeInterceptor(new DefaultReadOnlyToolClassifier(properties));
    }

    @AfterEach
    void tearDown() {
        BaseContext.clearSessionMode();
    }

    private ToolExecutionContext ctx(String tool) {
        return new ToolExecutionContext(tool, "test", "{}", "conv-1", 1L, 10L);
    }

    @Test
    void planBlocksNonReadOnlyToolWithVisibleReason() {
        BaseContext.setSessionMode("PLAN");

        ToolDecision decision = interceptor.beforeExecute(ctx("util.http"));

        assertThat(decision.type()).isEqualTo(ToolDecision.Type.DENY);
        assertThat(decision.reason()).contains("PLAN");
        assertThat(decision.reason()).contains("计划");
    }

    @Test
    void planAllowsProvablyReadOnlyToolWithoutExemptionSwitch() {
        // 豁免开关关闭，但 PLAN 约束的只读判定不受其门控（显式选 plan 即是开关）
        BaseContext.setSessionMode("PLAN");

        assertThat(interceptor.beforeExecute(ctx("util.search")).type())
                .isEqualTo(ToolDecision.Type.CONTINUE);
    }

    @Test
    void planBlocksCodeExecuteUnlessHeuristicProvesReadOnly() {
        BaseContext.setSessionMode("PLAN");
        ToolExecutionContext unsafeCode = new ToolExecutionContext("code.execute", "test",
                "{\"language\":\"python\",\"code\":\"open('/tmp/x','w')\"}", "c", 1L, 1L, null);

        assertThat(interceptor.beforeExecute(unsafeCode).type()).isEqualTo(ToolDecision.Type.DENY);
    }

    @Test
    void buildAndUnsetDoNotInterfere() {
        BaseContext.setSessionMode("BUILD");
        assertThat(interceptor.beforeExecute(ctx("util.http")).type())
                .isEqualTo(ToolDecision.Type.CONTINUE);

        BaseContext.clearSessionMode();
        assertThat(interceptor.beforeExecute(ctx("util.http")).type())
                .isEqualTo(ToolDecision.Type.CONTINUE);
    }

    @Test
    void yoloSkippedHereAndHandledByPipelineApprovalStep() {
        // YOLO 不在本拦截器介入（审批跳过在管线层）；此处必须 CONTINUE
        BaseContext.setSessionMode("YOLO");
        assertThat(interceptor.beforeExecute(ctx("util.http")).type())
                .isEqualTo(ToolDecision.Type.CONTINUE);
    }

    @Test
    void classifierExceptionFailsClosedInPlanMode() {
        BaseContext.setSessionMode("PLAN");
        SessionModeInterceptor failing = new SessionModeInterceptor(context -> {
            throw new IllegalStateException("分类器故障");
        });

        assertThat(failing.beforeExecute(ctx("util.search")).type())
                .isEqualTo(ToolDecision.Type.DENY);
    }

    @Test
    void runsAfterPlatformDenyListInterceptor() {
        // 平台 DENY 名单（HIGHEST_PRECEDENCE）优先于会话模式语义
        assertThat(interceptor.getOrder()).isGreaterThan(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void modeIsNormalizedCaseInsensitively() {
        BaseContext.setSessionMode("plan");
        assertThat(interceptor.beforeExecute(ctx("util.http")).type())
                .isEqualTo(ToolDecision.Type.DENY);
    }
}
