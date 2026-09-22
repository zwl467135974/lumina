# 生命周期 Hook 接入指南（决策型合规门）

> v3.14 批次 3.1 · 借鉴 ZCode hooks / DSH B1 · 组件：`io.lumina.agent.hook`

## 这是什么

企业合规门（脱敏、外发审计、输入风控、完成标准校验）实现 `AgentLifecycleHook` 接口并注册为 Spring Bean，即可介入 Agent 轮次的关键**决策点**——不改引擎、不碰业务代码。与既有机制的区别：

| 机制 | 性质 | 能力 |
|------|------|------|
| `AgentTurnEvent` 事件总线 | 单向观测 | 指标/审计/计费，不能影响执行 |
| `ToolSecurityPipeline` 拦截器 | 平台安全策略 | DENY/ASK/审批/守卫，管工具调用安全 |
| **`AgentLifecycleHook`** | **同步决策** | **可拒绝、可改写输入、可注入上下文、可阻止 premature 结束** |

## 决策点与决策词汇

```
轮次开始
  ├─ SessionStart        会话首轮（历史记忆为空）    → ADD_CONTEXT（注入会话级上下文）
  ├─ UserPromptSubmit    用户输入提交 LLM 前         → DENY / REPLACE_INPUT / ADD_CONTEXT
  ├─ PreToolUse          工具执行前（安全管线之前）   → DENY
  ├─ PostToolUse         工具执行成功后（观测）       → 无决策
  ├─ PostToolUseFailure  工具执行失败后（观测）       → 无决策
  └─ Stop                产出最终回答后              → CONTINUE（判定未达完成标准，注入理由续跑）
轮次结束
```

各点只认指定动作，返回其他动作按中立处理并告警（如 SessionStart 的 DENY 会被忽略——会话首启不存在可拒绝对象）。

**PreToolUse 在安全管线之前**：便宜的企业检查先行，避免先进入人工审批再被钩子拒绝。被钩子拒绝的调用不触发熔断计数（策略否决不是工具故障），拒绝理由对模型可见以便自纠。

## 示例：PII 脱敏门

```java
@Component
public class PiiGuardHook implements AgentLifecycleHook {

    @Override
    public String getName() {
        return "pii-guard";
    }

    @Override
    public HookDecision onUserPromptSubmit(AgentLifecycleHook.PromptSubmitInput input) {
        String masked = PiiMasker.mask(input.prompt());
        return masked.equals(input.prompt())
                ? HookDecision.allow()
                : HookDecision.replaceInput(masked, "已脱敏身份证号与手机号");
    }

    @Override
    public HookDecision onStop(AgentLifecycleHook.StopInput input) {
        // 完成标准校验：要求交付物包含结论段落
        return input.responseText().contains("结论") || input.continueCount() >= 1
                ? HookDecision.allow()
                : HookDecision.continueTurn("回答缺少结论段落，请补充");
    }
}
```

开启配置（`nacos-config/lumina-agent-service.yaml`）：

```yaml
lumina:
  agent:
    hooks:
      enabled: true        # 默认 false；零钩子时本就空转，开关是合规介入的显式授权
      timeout-ms: 5000
      max-stop-continues: 3
```

## 组合语义（多钩子）

钩子按 `getOrder()` 升序执行（默认 0）：

- **DENY 支配且短路**——任一拒绝立即生效，后续钩子不再执行
- **REPLACE_INPUT 首个生效**——后续替换告警忽略（可与 ADD_CONTEXT 叠加）
- **ADD_CONTEXT 累积合并**——多钩子上下文以换行拼接
- **CONTINUE 首个生效**——Stop 点首个继续判定生效

## 工程契约（引擎对钩子的保护与约束）

| 契约 | 行为 |
|------|------|
| 超时保护 | 单钩子超过 `timeout-ms`（默认 5s）按**中立放行**处理（守护线程池限时执行），绝不崩回合；卡死的实现线程不阻塞轮次 |
| 异常隔离 | 钩子抛异常按中立放行 + WARN 日志 + 计数；需要强一致的合规门应在实现内捕获自身故障并显式返回 deny |
| 输出限额 | reason（500）/ replacement（20000）/ additionalContext（8000）超限截断并告警，防钩子输出撑爆上下文 |
| Stop 防循环 | CONTINUE 每回合上限 `max-stop-continues`（默认 3），达上限强制结束并告警 |
| 审计可见 | 所有阻断路径带钩子名 + 理由的 WARN 日志；UserPromptSubmit 拒绝走 `ErrorCode.AGENT_INPUT_DENIED`（403），理由进失败结果；PreToolUse 拒绝理由对模型可见 |
| 指标 | `agent.hook.invocation` 计数器（hook/point/outcome=ok/timeout/error/interrupted） |

## 流式与同步路径

UserPromptSubmit/SessionStart/PreToolUse/PostToolUse/Stop 六个点在**同步与流式管线均生效**。差异：

- UserPromptSubmit 拒绝：同步路径返回失败结果（403 语义）；流式路径返回 `ERROR` chunk（含理由）
- Stop CONTINUE：两路径均把"上一段回答 + 继续指令"并入上下文再执行一段（PlanAndExecute 型会以累积上下文重规划）

## 边界说明

- 钩子在轮次关键路径上**同步执行**，实现必须快速返回；重活（外呼审计服务）请在实现内异步化并只做轻量放行判断
- 超时/异常默认**放行**（fail-open）——这是"钩子故障不瘫痪平台"与"合规门必须显式表达拒绝"的平衡点；强一致场景在实现内 try/catch 后返回 deny
- Stop 钩子面向"完成标准校验"；每次 CONTINUE 都是一轮完整模型调用（有成本），理由应具体可执行
- 与 `sessionMode`（PLAN/BUILD/YOLO）正交：PLAN 模式下非只读工具已被构造性阻断，PreToolUse 钩子见到的是已过滤后的调用流
