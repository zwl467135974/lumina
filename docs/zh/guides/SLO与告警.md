# Lumina SLO 与告警手册

> 目标读者：运维 Lumina 部署的工程师。回答三个问题——我们承诺什么（SLO）、
> 什么时候叫人（告警）、叫来之后干什么（处置）。
>
> 配套文件：告警规则 `docker/prometheus/alerts/lumina-alerts.yml`，
> 分级路由 `docker/prometheus/alertmanager.yml`，监控栈
> `docker-compose-monitoring.yml`（Prometheus + Alertmanager + Grafana）。

## 一、SLO 定义

| # | 服务水平目标 | 指标 | 目标值 | 支撑指标来源 |
|---|-------------|------|--------|-------------|
| SLO-1 | API 可用性（月） | 非 5xx 请求占比 | ≥ 99.5% | `http_server_requests_seconds_count{status}` |
| SLO-2 | Agent 轮次成功率 | FAILED / (FAILED+COMPLETED) | ≤ 10% | `agent_turn_count_total{result}` |
| SLO-3 | Agent 轮次时延 | p95 | < 60s（按模型/工具集校准） | `agent_turn_duration_seconds` |
| SLO-4 | 状态一致性 | 状态保存失败次数 | 0 | `agent_state_save_errors_total` |
| SLO-5 | 配置一致性 | 热更新失败次数 | 0 | `agent_config_reload_errors_total` |

**语义约定**：
- INTERRUPTED **不计入**失败率——中断 ≠ 失败（结果未知，仅幂等操作可安全
  重试，见防御性设计原则）。它由独立告警（激增）监控，正常发布窗口会有
  自然增量。
- SLO-3 的 60s 是默认参考值：纯文本轻 Agent 与重工具编排差一个量级，
  部署时应按自身 P95 实测校准阈值，不要盲抄。

## 二、告警分级与响应

| 级别 | 含义 | 响应要求 | 当前规则 |
|------|------|---------|---------|
| critical | SLO 正在被燃烧 / 状态可能静默漂移 | 1h 内响应 | InstanceDown、Http5xxRateHigh、StateSaveErrors |
| warning | 趋势偏离，尚在预算内 | 当日处理 | TurnErrorRateHigh、TurnSlowP95、ConfigReloadErrors、QdrantErrors、JvmMemoryHigh、HttpLatencyHigh、InterruptedSpike |

Alertmanager 分级路由：critical 10s 聚合、1h 重复提醒；默认 4h 重复。
通知渠道当前为占位 webhook，接入时替换 `alertmanager.yml` 中的 url。

## 三、处置手册（Runbook）

### LuminaInstanceDown / LuminaHttp5xxRateHigh
1. `docker ps` / 编排平台确认实例状态；看重启原因（OOM？`docker inspect` exit code）。
2. 5xx 比例高先看网关日志 `traceId` 聚类的错误分布——区分下游 LLM provider
   故障（429/超时）与自身异常。
3. LLM provider 侧故障确认后：观察 Resilience4j 熔断是否生效（Grafana
   Tools 面板），必要时手动摘除 provider 配置。

### LuminaAgentTurnErrorRateHigh
1. 按 `result=FAILED` 与日志 traceId 对齐，分失败原因聚类：工具失败 /
   上下文溢出恢复失败 / provider 错误。
2. 工具失败集中 → 看 Agent 详情页"工具使用分析"的失败率列（V54），确认
   是否某工具批量故障。
3. 溢出恢复失败 → 检查 `contextWindowTokens` 配置与该 Agent 的系统提示
   体积（估算器低估代码类输入时会依赖溢出自愈，`max-overflow-retries=1`）。

### LuminaAgentTurnInterruptedSpike（非发布窗口）
1. 确认是否有非计划的实例重启/崩溃（对账日志"心跳消失"关键字）。
2. 多实例部署重点核对：滚动发布是否错峰（ADR-002 的对账隔离生效前提是
   各实例心跳正常）。
3. 确认受影响任务是否需要人工重放（仅幂等业务可直接重试）。

### LuminaAgentStateSaveErrors（critical，SLO-4）
状态漂移无感知期，**优先级高于大多数告警**：
1. Redis 连通性与内存（`docker stats`、`redis-cli info memory`）。
2. 序列化失败会在日志中带具体类名——对照最近发布是否改动了状态对象结构。

### LuminaConfigReloadErrors
1. Nacos/配置源可用性。
2. 失败的实例将持续以旧配置运行到缓存 TTL——修复后确认指标回落，
   必要时滚动重启对齐。

## 四、验收与调参

- 规则正确性：`promtool check rules docker/prometheus/alerts/lumina-alerts.yml`
  （监控栈容器内自带 promtool；`docker run --rm -v $PWD/docker/prometheus:/p prom/prometheus:v2.54.1 promtool check rules /p/alerts/lumina-alerts.yml`）。
- 阈值调参唯一入口是 alerts 文件本身，改动后 `docker compose -f
  docker-compose-monitoring.yml restart prometheus`。
- 新增指标必须同步补规则或注明"暂不告警"，避免"可观测止步于可看"
  （这是 v3.12 评审指出的缺口，本手册即整改）。

## 五、已知盲区（诚实声明）

- Resilience4j 熔断器指标未在告警规则中（注册与否待验证）——验证
  `resilience4j_circuitbreaker_state` 存在后应补熔断打开告警。
- SSE 流式连接数 / 背压暂无专用指标（v3.12 评审 C3 项），补埋点前无法告警。
- 压测容量基线（v3.12 评审 P1-5）未建立——SLO-1/SLO-3 的目标值在真实
  负载下的可达性未经压测验证，见 `docs/zh/guides/容量压测指南.md`。
