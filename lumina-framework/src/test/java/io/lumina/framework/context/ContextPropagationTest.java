package io.lumina.framework.context;

import io.lumina.common.core.BaseContext;
import io.lumina.common.core.LoginContext;
import io.micrometer.context.ContextRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link LoginContext} 在 Reactor 线程切换（boundedElastic）下自动传播
 *
 * <p>该测试复现并守护修复目标：Agent 执行引擎与工具调用切到线程池后，
 * {@link BaseContext}（租户/用户/角色/权限）不再丢失，保障多租户隔离正确性。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
class ContextPropagationTest {

    /**
     * 模拟 {@link ContextPropagationAutoConfiguration} 的初始化效果：
     * 注册 accessor 并启用 Reactor 全局自动上下文传播
     */
    @BeforeAll
    static void enablePropagation() {
        ContextRegistry.getInstance()
                .registerThreadLocalAccessor(new LoginContextThreadLocalAccessor())
                .registerThreadLocalAccessor(new ConversationIdThreadLocalAccessor());
        Hooks.enableAutomaticContextPropagation();
    }

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * 核心：tenantId 在 boundedElastic 线程中可读（修复前为 null，导致 SQL 跳过租户过滤）
     */
    @Test
    void tenantIdPropagatesToBoundedElasticThread() {
        BaseContext.setCurrent(new LoginContext(100L, 1L, "tester",
                new String[]{"TENANT_ADMIN"}, new String[]{"system:user:list"}));

        Long tenantIdInReactor = Mono.fromCallable(BaseContext::getTenantId)
                .subscribeOn(Schedulers.boundedElastic())
                .block();

        assertThat(tenantIdInReactor)
                .as("boundedElastic 线程应能读到主线程设置的 tenantId")
                .isEqualTo(100L);
    }

    /**
     * 完整快照（租户/用户/用户名/角色）跨线程传播
     */
    @Test
    void fullSnapshotPropagatesAcrossThreadSwitch() {
        BaseContext.setCurrent(new LoginContext(200L, 2L, "admin",
                new String[]{"SUPER_ADMIN"}, new String[0]));

        LoginContext snapshot = Mono.fromCallable(BaseContext::current)
                .subscribeOn(Schedulers.boundedElastic())
                .block();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.tenantId()).isEqualTo(200L);
        assertThat(snapshot.userId()).isEqualTo(2L);
        assertThat(snapshot.username()).isEqualTo("admin");
        assertThat(snapshot.roles()).contains("SUPER_ADMIN");
    }

    /**
     * 模拟 {@code ToolDefinitionToAgentToolAdapter.callAsync} 的真实场景：
     * 嵌套 subscribeOn（外层 Agent 执行 + 内层工具执行）下上下文仍不丢
     */
    @Test
    void contextSurvivesNestedSubscribeOn() {
        BaseContext.setCurrent(new LoginContext(300L, 3L, "agent",
                new String[]{"TENANT_USER"}, new String[]{"agent:execute"}));

        Long tenantId = Mono.fromCallable(() ->
                        // 内层再次切换线程（模拟工具执行）
                        Mono.fromCallable(BaseContext::getTenantId)
                                .subscribeOn(Schedulers.boundedElastic())
                                .block())
                .subscribeOn(Schedulers.boundedElastic())
                .block();

        assertThat(tenantId).isEqualTo(300L);
    }

    /**
     * 权限判定在异步线程中仍生效（BaseContext.hasPermission 走 ThreadLocal）
     */
    @Test
    void permissionCheckWorksInReactorThread() {
        BaseContext.setCurrent(new LoginContext(400L, 4L, "user",
                new String[]{"TENANT_USER"}, new String[]{"system:user:list"}));

        Boolean hasPermission = Mono.fromCallable(() -> BaseContext.hasPermission("system:user:list"))
                .subscribeOn(Schedulers.boundedElastic())
                .block();

        assertThat(hasPermission).isTrue();
    }

    /**
     * conversationId 在嵌套 boundedElastic 线程中可读
     *
     * <p>模拟 {@code ToolDefinitionToAgentToolAdapter.callAsync} 真实场景：
     * Agent 执行入口设置 conversationId 后，工具适配器在 boundedElastic 线程中
     * 调用 {@link BaseContext#getConversationId()} 应能读到正确值。
     */
    @Test
    void conversationIdSurvivesNestedSubscribeOn() {
        BaseContext.setConversationId("conv-test-123");

        String conversationId = Mono.fromCallable(() ->
                        Mono.fromCallable(BaseContext::getConversationId)
                                .subscribeOn(Schedulers.boundedElastic())
                                .block())
                .subscribeOn(Schedulers.boundedElastic())
                .block();

        assertThat(conversationId)
                .as("嵌套 boundedElastic 线程应能读到主线程设置的 conversationId")
                .isEqualTo("conv-test-123");
    }
}
