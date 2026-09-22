package io.lumina.agent.tool.security;

import io.lumina.common.core.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 会话模式拦截器（v3.13 PLAN / BUILD / YOLO，运行时级策略强制）
 *
 * <p>模式经 {@link BaseContext#getSessionMode()} 读取（业务层从请求参数
 * 装入 {@code AgentConfig.sessionMode}，引擎入口写入，与 conversationId
 * 同生命周期）。策略强制在管线层而非提示词层——模型无法通过提示注入绕过。
 *
 * <p>PLAN 模式（本拦截器唯一介入的模式）：
 * <ul>
 *   <li>可证明只读（{@link ReadOnlyToolClassifier#isProvablyReadOnly}，
 *       纯判定、不受豁免开关影响）→ 放行</li>
 *   <li>其余（含判不定的）→ <b>DENY</b>，拒绝理由对模型可见并指引
 *       "先产出计划、获批准后切换 BUILD 执行"——构造性阻断写操作</li>
 * </ul>
 *
 * <p>BUILD / 未设置：不介入（默认审批管线语义）。YOLO：本拦截器不介入，
 * 由管线在审批步跳过人工审批（平台 DENY 名单在本拦截器之前已生效，
 * 单调守卫在其之后仍然生效）。
 *
 * <p>顺序：排在 {@link ConfigToolInterceptor}（平台 DENY 名单）之后——
 * 平台禁用优先于一切模式语义。
 *
 * @author Lumina Team
 * @since 3.13.0
 */
@Slf4j
@Component
public class SessionModeInterceptor implements ToolExecutionInterceptor {

    /** PLAN 模式拒绝理由（对模型可见，指引自纠：产出计划而非尝试写操作） */
    static final String PLAN_DENY_REASON =
            "当前会话处于 PLAN 模式（只读规划阶段）：该工具调用不是可证明只读的操作，已被构造性阻断。"
                    + "请先完成调研并产出执行计划；计划获得批准、会话切换到 BUILD 模式后再执行写操作。";

    private final ReadOnlyToolClassifier readOnlyClassifier;

    public SessionModeInterceptor(ReadOnlyToolClassifier readOnlyClassifier) {
        this.readOnlyClassifier = readOnlyClassifier;
    }

    @Override
    public int getOrder() {
        // 平台 DENY 名单（HIGHEST_PRECEDENCE）之后、其他策略之前
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public ToolDecision beforeExecute(ToolExecutionContext context) {
        String mode = BaseContext.getSessionMode();
        if (!"PLAN".equals(mode)) {
            return ToolDecision.CONTINUE;
        }
        boolean readonly;
        try {
            readonly = readOnlyClassifier.isProvablyReadOnly(context);
        } catch (Exception e) {
            // fail-closed：分类器故障时 PLAN 模式按非只读处理（阻断）
            log.warn("PLAN 模式只读判定异常，按阻断处理（fail-closed）: tool={}, error={}",
                    context.getToolName(), e.getMessage());
            readonly = false;
        }
        if (readonly) {
            return ToolDecision.CONTINUE;
        }
        log.info("PLAN 模式阻断工具调用: tool={}, conversationId={}",
                context.getToolName(), context.getConversationId());
        return ToolDecision.deny(PLAN_DENY_REASON);
    }
}
