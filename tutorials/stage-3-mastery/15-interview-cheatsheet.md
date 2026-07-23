# 15 — 面试八股速查（40 考点 + 项目实例）

> **这是整个教学体系的收官篇。面试前一晚看这个。**

---

## 怎么用

每个考点三列：**面试题 → 一句话答案 → Lumina 项目里哪里能找到实例**。

面试时说"我在 XX 项目里实际用过这个"——比纯背八股有说服力 10 倍。

---

## Spring 框架（10 题）

| # | 考点 | 一句话答案 | Lumina 实例 |
|---|------|-----------|-------------|
| 1 | Bean 作用域 | 默认单例（singleton） | 所有 @Service |
| 2 | Bean 生命周期 | 实例化→注入→Aware→前后置→初始化→销毁 | @PostConstruct in TenantLineHandlerImpl |
| 3 | 三级缓存 | singleton→earlySingleton→singletonFactory 解循环依赖 | 任何 @Autowired |
| 4 | AOP 用的什么代理 | 接口用 JDK 代理，类用 CGLIB | @Audit/@Transactional |
| 5 | AOP 同类调用失效 | this.method() 绕过代理 | （项目避免自调用） |
| 6 | @Transactional 失效场景 | 自调用/非public/吞异常/rollbackFor | `rollbackFor=Exception.class` |
| 7 | 事务传播 REQUIRED | 有就加入，没有就新建（默认） | AgentServiceImpl.createAgent |
| 8 | 自动配置原理 | 读 AutoConfiguration.imports + @ConditionalOnXxx | lumina-framework 的 8 个配置类 |
| 9 | @ConditionalOnMissingBean | 用户优先，默认可被覆盖 | RedisConfig.redissonClient |
| 10 | 构造器注入 vs 字段注入 | final不可变/可测试/编译期检查 | @RequiredArgsConstructor |

---

## MyBatis（5 题）

| # | 考点 | 一句话答案 | Lumina 实例 |
|---|------|-----------|-------------|
| 11 | BaseMapper | 继承它免费拿 20+ CRUD | AgentMapper（3 行） |
| 12 | 逻辑删除 | @TableLogic，DELETE 变 UPDATE | LlmProviderDO.deleted |
| 13 | 自动填充 | @TableField(fill=INSERT) + MetaObjectHandler | createTime/updateTime |
| 14 | 多租户拦截器原理 | JSqlParser 解析 SQL 追加 tenant_id | TenantLineHandlerImpl |
| 15 | 拦截器顺序 | 多租户→分页，反了报错 | MybatisPlusTenantConfig |

---

## Redis（5 题）

| # | 考点 | 一句话答案 | Lumina 实例 |
|---|------|-----------|-------------|
| 16 | 分布式锁演进 | SETNX→SET NX PX→UUID→Lua→Redisson看门狗 | AgentTriggerServiceImpl.fireWithLock |
| 17 | Redisson 看门狗 | 每 10 秒续期，线程死了不续 | RLock.tryLock |
| 18 | 缓存穿透 | 查不存在→缓存空值 | （可扩展实现） |
| 19 | 缓存雪崩 | 同时过期→随机 TTL | 不同业务不同 TTL |
| 20 | 缓存击穿 | 热点过期→互斥锁 | （可扩展实现） |

---

## 并发（5 题）

| # | 考点 | 一句话答案 | Lumina 实例 |
|---|------|-----------|-------------|
| 21 | ThreadLocal | 每线程独立副本 | BaseContext |
| 22 | ThreadLocal 内存泄漏 | 线程池不 clear→残留→afterCompletion 必须 clear | TenantIsolationInterceptor |
| 23 | 线程池 7 参数 | 核心/最大/存活/单位/队列/工厂/拒绝策略 | agentTaskExecutor |
| 24 | 线程池执行顺序 | 核心→队列→最大→拒绝 | （标准流程） |
| 25 | CallerRunsPolicy | 调用者执行=反压 | agentTaskExecutor 的拒绝策略 |

---

## 分布式与安全（8 题）

| # | 考点 | 一句话答案 | Lumina 实例 |
|---|------|-----------|-------------|
| 26 | JWT vs Session | JWT 无状态多实例，Session 有状态难扩展 | StandaloneJwtFilter |
| 27 | JWT 防伪造 | HMAC 签名，篡改 Payload→签名不匹配 | JwtUtil.validateToken |
| 28 | JWT 登出失效 | Redis 黑名单 | RedisCacheManager |
| 29 | 防伪造身份头 | 过滤器先剥离 X-* 再注入可信值 | IdentityHeaderRequestWrapper |
| 30 | 熔断三状态 | Closed→Open→Half-Open | LlmResilienceWrapper |
| 31 | 为什么需要熔断 | 防级联故障（下游挂了不拖垮自己） | LLM 调用保护 |
| 32 | 重试套熔断 | 洋葱装饰器 Retry→CircuitBreaker | LlmResilienceWrapper.execute |
| 33 | fail-open/closed | 依赖挂了：放行(可用性) vs 拒绝(安全) | AgentRateLimiter 默认 fail-closed |

---

## 响应式与协议（4 题）

| # | 考点 | 一句话答案 | Lumina 实例 |
|---|------|-----------|-------------|
| 34 | Flux vs Mono | Flux=0-N，Mono=0-1 | executeStream(Flux) / execute(Mono) |
| 35 | 背压 | 消费者控制生产者速度 | onBackpressureBuffer |
| 36 | SSE vs WebSocket | SSE 单向简单，WebSocket 双向复杂 | AgentController.executeAgentStream |
| 37 | chunked transfer | 分块传输不用预知总长度 | SSE 响应 |

---

## 架构设计（3 题）

| # | 考点 | 一句话答案 | Lumina 实例 |
|---|------|-----------|-------------|
| 38 | 分层架构 | Controller→Service→Domain→Infra，各司其职 | AgentController→AgentServiceImpl→Agent→AgentMapper |
| 39 | DTO/VO/DO 分离 | 安全(脱敏)+解耦(前后端不同步) | CreateAgentDTO/AgentVO/AgentDO |
| 40 | 多租户隔离 | 共享DB+tenant_id 行级隔离 | TenantLineHandlerImpl |

---

## 面试加分话术

### 当面试官问"你用过 XX 吗"

> "我在 Lumina 项目里用过。比如分布式锁，我们用 Redisson 的 RLock 给 Cron 触发器加锁，防止多实例重复执行。代码在 AgentTriggerServiceImpl 的 fireWithLock 方法里，用 tryLock(0, 300) 非阻塞获取，finally 里 isHeldByCurrentThread 检查后释放。"

### 当面试官追问原理

> "Redisson 的看门狗机制每 10 秒检查持锁线程是否存活，活着就续期。如果进程崩溃了不续期，锁 30 秒后自动释放，不会死锁。"

### 当面试官问"遇到过什么坑"

> "Redis 的 RAtomicLong 和 RBucket 类型不匹配导致 TTL 不生效——我们封装了 incrementAndGetWithExpire 方法保证用同一个对象操作。还有 MyBatis 多租户拦截器必须在分页拦截器之前注册，否则 COUNT 重写会报参数索引越界。"

---

## 🎓 整个教学体系到此全部完成！

```
✅ 第一阶段：技术栈基础     17 篇
✅ 第二阶段：项目实战应用   15 篇
✅ AI 专项                47 篇
✅ 第三阶段：原理深潜       15 篇
                           ────
总计                      94 篇 + 5 个导读 = 99 个文件
```

你现在是一个**既懂企业级全栈开发，又懂 AI Agent 架构，还能讲清底层原理的稀缺人才**。

面试去吧，稳了 🚀

---

📝 **本篇撰写期间修正的代码**：无。
