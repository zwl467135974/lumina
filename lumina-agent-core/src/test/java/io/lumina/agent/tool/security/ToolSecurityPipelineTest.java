package io.lumina.agent.tool.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ToolSecurityPipeline 单元测试
 *
 * <p>重点验证单调守卫语义：守卫没有 allow 臂，拦截器排序/审批放行
 * 都无法翻转守卫的否决；全程 fail-closed。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
class ToolSecurityPipelineTest {

    private ToolExecutionContext ctx(String tool) {
        return new ToolExecutionContext(tool, "test", "{}", "conv-1", 1L, 10L);
    }

    private ToolExecutionInterceptor returning(ToolDecision decision) {
        return new ToolExecutionInterceptor() {
            @Override
            public ToolDecision beforeExecute(ToolExecutionContext context) {
                return decision;
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };
    }

    private ToolGuard denying(final String denial) {
        return context -> denial;
    }

    @Test
    void allowsWhenAllAbstain() {
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.CONTINUE)), List.of(denying(null)), null);

        assertThat(pipeline.check(ctx("util.httpRequest"))).isNull();
    }

    @Test
    void interceptorDenyStopsImmediately() {
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.deny("平台禁用"))), List.of(denying(null)), null);

        assertThat(pipeline.check(ctx("util.httpRequest"))).isEqualTo("平台禁用");
    }

    @Test
    void askWithoutApprovalPortFailsClosed() {
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.ask("高危工具"))), List.of(denying(null)), null);

        assertThat(pipeline.check(ctx("code.execute"))).contains("未获批准");
    }

    @Test
    void askWithApprovalRejectedFailsClosed() {
        ToolApprovalPort rejectingPort = (context, reason) -> false;
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.ask("高危工具"))), List.of(denying(null)), rejectingPort);

        assertThat(pipeline.check(ctx("code.execute"))).contains("未获批准");
    }

    @Test
    void askWithApprovalAllowedPassesWhenGuardAbstains() {
        ToolApprovalPort approvingPort = (context, reason) -> true;
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.ask("高危工具"))), List.of(denying(null)), approvingPort);

        assertThat(pipeline.check(ctx("code.execute"))).isNull();
    }

    @Test
    void guardDenyCannotBeOverriddenByInterceptorOrderOrApproval() {
        // 单调性核心用例：审批放行 + 拦截器弃权，守卫仍然否决
        ToolApprovalPort approvingPort = (context, reason) -> true;
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.ask("高危工具"))),
                List.of(denying("租户黑名单工具")), approvingPort);

        assertThat(pipeline.check(ctx("code.execute"))).isEqualTo("租户黑名单工具");
    }

    @Test
    void interceptorExceptionFailsClosed() {
        ToolExecutionInterceptor throwing = new ToolExecutionInterceptor() {
            @Override
            public ToolDecision beforeExecute(ToolExecutionContext context) {
                throw new IllegalStateException("策略存储不可用");
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(List.of(throwing), List.of(), null);

        assertThat(pipeline.check(ctx("util.httpRequest"))).contains("fail-closed");
    }

    @Test
    void guardExceptionFailsClosed() {
        ToolGuard throwing = context -> {
            throw new IllegalStateException("权限服务不可用");
        };
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(List.of(), List.of(throwing), null);

        assertThat(pipeline.check(ctx("util.httpRequest"))).contains("fail-closed");
    }

    @Test
    void approvalPortExceptionFailsClosed() {
        ToolApprovalPort throwing = (context, reason) -> {
            throw new IllegalStateException("通知服务不可用");
        };
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.ask("高危工具"))), List.of(), throwing);

        assertThat(pipeline.check(ctx("code.execute"))).contains("未获批准");
    }

    @Test
    void interceptorsRunInOrder() {
        StringBuilder order = new StringBuilder();
        ToolExecutionInterceptor first = new ToolExecutionInterceptor() {
            @Override
            public ToolDecision beforeExecute(ToolExecutionContext context) {
                order.append("first,");
                return ToolDecision.CONTINUE;
            }

            @Override
            public int getOrder() {
                return 1;
            }
        };
        ToolExecutionInterceptor second = new ToolExecutionInterceptor() {
            @Override
            public ToolDecision beforeExecute(ToolExecutionContext context) {
                order.append("second");
                return ToolDecision.CONTINUE;
            }

            @Override
            public int getOrder() {
                return 2;
            }
        };
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(List.of(second, first), List.of(), null);

        assertThat(pipeline.check(ctx("t"))).isNull();
        assertThat(order.toString()).isEqualTo("first,second");
    }

    @Test
    void configInterceptorDenyTakesPrecedenceOverAsk() {
        io.lumina.agent.config.LuminaAgentProperties props = new io.lumina.agent.config.LuminaAgentProperties();
        props.getTool().getSecurity().getDenyTools().add("code.execute");
        props.getTool().getSecurity().getApprovalTools().add("code.execute");
        ConfigToolInterceptor interceptor = new ConfigToolInterceptor(props);

        assertThat(interceptor.beforeExecute(ctx("code.execute")).type())
                .isEqualTo(ToolDecision.Type.DENY);
        assertThat(interceptor.beforeExecute(ctx("util.calculate")).type())
                .isEqualTo(ToolDecision.Type.CONTINUE);
    }

    // ==================== 只读豁免（v3.13） ====================

    @Test
    void readonlyExemptionSkipsApprovalWithoutPort() {
        // ASK + 可证明只读：无审批端口也放行（豁免生效）
        ReadOnlyToolClassifier readonly = context -> true;
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.ask("高危工具"))), List.of(denying(null)), null, readonly);

        assertThat(pipeline.check(ctx("util.search"))).isNull();
    }

    @Test
    void readonlyExemptionDoesNotTouchApprovalPort() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        ToolApprovalPort countingPort = (context, reason) -> {
            calls.incrementAndGet();
            return false;
        };
        ReadOnlyToolClassifier readonly = context -> true;
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.ask("高危工具"))), List.of(denying(null)), countingPort, readonly);

        assertThat(pipeline.check(ctx("util.search"))).isNull();
        assertThat(calls.get()).isZero();
    }

    @Test
    void readonlyExemptionStillRespectsGuardVeto() {
        // 单调性：只读豁免不能翻转守卫否决
        ReadOnlyToolClassifier readonly = context -> true;
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.ask("高危工具"))),
                List.of(denying("租户黑名单工具")), null, readonly);

        assertThat(pipeline.check(ctx("code.execute"))).isEqualTo("租户黑名单工具");
    }

    @Test
    void readonlyExemptionDoesNotOverrideDeny() {
        // DENY 在拦截器链立即返回，豁免层根本不会执行
        ReadOnlyToolClassifier readonly = context -> true;
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.deny("平台禁用"))), List.of(denying(null)), null, readonly);

        assertThat(pipeline.check(ctx("code.execute"))).isEqualTo("平台禁用");
    }

    @Test
    void classifierNotReadOnlyStillRequiresApproval() {
        ReadOnlyToolClassifier readonly = context -> false;
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.ask("高危工具"))), List.of(denying(null)), null, readonly);

        assertThat(pipeline.check(ctx("code.execute"))).contains("未获批准");
    }

    @Test
    void classifierExceptionFailsClosedToApproval() {
        ReadOnlyToolClassifier throwing = context -> {
            throw new IllegalStateException("分类器内部错误");
        };
        // 无审批端口 → 拒绝（绝不因分类器故障放行）
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.ask("高危工具"))), List.of(denying(null)), null, throwing);

        assertThat(pipeline.check(ctx("code.execute"))).contains("未获批准");
    }

    @Test
    void noClassifierKeepsLegacyBehavior() {
        // 未注入分类器（旧装配路径）：ASK 仍走审批
        ToolSecurityPipeline pipeline = new ToolSecurityPipeline(
                List.of(returning(ToolDecision.ask("高危工具"))), List.of(denying(null)), null);

        assertThat(pipeline.check(ctx("util.search"))).contains("未获批准");
    }
}
