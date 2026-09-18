# ADR-004：Token 估算校准回路（设计定稿，实现分期）

- 状态：已接受设计，实现待数据（v3.12.1 记录）
- 关联代码：`TokenEstimator` / `DefaultAgentExecutionEngine`（agent-core）

## 背景

`TokenEstimator` 是启发式（CJK 1:1、其他 4:1、图片固定 1000），对代码/JSON
密集输入系统性低估，依赖溢出自愈兜底。v3.12 评审指出：每轮 LLM 返回的真实
`usage` 就在手边，却没有回填校准——数据在流经时被浪费。

## 评估结论（为什么现在不直接实现）

完整回路需要把"装填时的估算值"从引擎四条执行路径（同步/流式/Plan-Execute/
Failover 链）穿透到 usage 读取点——对最核心的执行文件做大范围签名改动，
换一个观测指标，风险收益不成比例。且当前没有量化数据证明校准的必要性
幅度（低估 5% 还是 30%？前者溢出自愈足够，后者才值得校准）。

## 分期设计

### 第一期：观测（数据先行，低侵入）
- 在 `extractTokenUsage` 读取真实 usage 的同时，对**本次实际发送的消息**
  重新跑一遍估算（确定性重导出，无需穿透状态），按 provider 打标记录
  Micrometer `agent.token.estimation.ratio`（实际/估算，DistributionSummary）；
- Grafana 看分布：ratio 持续 >1.1 的 provider/内容形态即校准收益区。

### 第二期：校准（数据证明有必要后）
- 按 provider 维护 EMA 系数（RedisCacheManager，`agent:token:calibration:{provider}`）；
- `TokenEstimator` 增加带系数入口，装填预算时乘以系数（上下限 0.5~2.0 防震荡）；
- 系数更新异步、容错——校准失败不影响主路径（照抄 Sink 模式的容错纪律）。

### 明确不做
- 不用估算值计费（计费永远以 provider usage 为准——现有注释即此立场）；
- 不做按 Agent/按租户粒度的系数——先用 provider 粒度证明价值再细化。

## 后果

- 第一期落地时 SLO 文档补 `agent_token_estimation_ratio` 的告警/看板。
- 若第一期数据显示 ratio 集中在 0.95~1.1，关闭本 ADR 第二期（记录结论即可）。
