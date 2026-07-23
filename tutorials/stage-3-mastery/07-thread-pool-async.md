# 07 — 线程池原理

> **前置要求**：已完成 [06-ThreadLocal 上下文](06-threadlocal-context.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 面试题引入

> **"面试官：ThreadPoolExecutor 的 7 个参数是什么？4 种拒绝策略有什么区别？Lumina 异步任务怎么配置线程池？"**

---

## 表层回答（60 分）

7 参数：核心线程数、最大线程数、空闲存活时间、时间单位、工作队列、线程工厂、拒绝策略。4 种拒绝策略：Abort/CallerRuns/Discard/DiscardOldest。

---

## 深层原理（90 分）

### 7 个参数

```java
new ThreadPoolExecutor(
    int corePoolSize,        // 1. 核心线程数（常驻不销毁）
    int maximumPoolSize,     // 2. 最大线程数（含核心）
    long keepAliveTime,      // 3. 非核心线程空闲存活时间
    TimeUnit unit,           // 4. 时间单位
    BlockingQueue<Runnable> workQueue,   // 5. 工作队列
    ThreadFactory threadFactory,         // 6. 线程工厂（命名/守护线程等）
    RejectedExecutionHandler handler     // 7. 拒绝策略
);
```

### 任务执行流程（面试必画）

```
新任务提交
  ↓
核心线程满了吗？──No──► 创建核心线程执行
  │Yes
  ▼
队列满了吗？──No──► 放入队列等待
  │Yes
  ▼
达到最大线程数了吗？──No──► 创建非核心线程执行
  │Yes
  ▼
执行拒绝策略
```

**关键**：不是"先创建到最大线程再排队"，而是"先排到队列，队列满才创建新线程"！

---

## 4 种拒绝策略

| 策略 | 行为 | 适用场景 |
|------|------|----------|
| **AbortPolicy**（默认） | 抛 RejectedExecutionException | 重要任务（不能丢） |
| CallerRunsPolicy | 由提交任务的线程自己执行 | 不想丢任务 + 反压 |
| DiscardPolicy | 静默丢弃 | 可丢弃的任务 |
| DiscardOldestPolicy | 丢弃队列最老的任务 | 只要最新的 |

### CallerRunsPolicy 的妙用

```
线程池满了 → 调用者（HTTP 线程）自己执行任务
→ HTTP 线程被占住 → 不再接收新请求
→ 天然的"背压"（Backpressure）：让上游慢下来
```

---

## Lumina 的线程池

### 异步任务线程池

```java
// Lumina 配置了专门的线程池给异步 Agent 任务
@Bean("agentTaskExecutor")
public Executor agentTaskExecutor() {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
        4,                                      // 核心线程 4
        16,                                     // 最大线程 16
        60, TimeUnit.SECONDS,                   // 空闲 60 秒回收
        new LinkedBlockingQueue<>(100),         // 队列 100
        new ThreadFactoryBuilder()
            .setNameFormat("agent-task-%d")     // 命名（方便排查）
            .build(),
        new ThreadPoolExecutor.CallerRunsPolicy()   // 满了由调用者执行（反压）
    );
    return executor;
}
```

### 为什么用 CallerRunsPolicy

Agent 异步任务不能丢（用户提交了就要执行），但又不能无限创建线程（OOM）。CallerRunsPolicy 让"满了就阻塞提交者"——HTTP 线程被占住，自然限制了提交速度。

---

## 常见追问

### Q：核心线程能被回收吗？

**A**：默认不能。但 `allowCoreThreadTimeOut(true)` 可以让核心线程也超时回收。

### Q：队列用 LinkedBlockingQueue 还是 ArrayBlockingQueue？

**A**：Linked 无界（默认 Integer.MAX_VALUE，可能 OOM）；Array 有界。生产环境推荐有界队列（ArrayBlockingQueue 或指定容量的 Linked）。

### Q： Executors.newFixedThreadPool 为什么不推荐？

**A**：它用的 LinkedBlockingQueue 无界——任务堆积 OOM。阿里规范禁止用 Executors，必须手动 new ThreadPoolExecutor。

---

## 小结

| 知识点 | 一句话 |
|--------|--------|
| 执行顺序 | 核心→队列→最大→拒绝 |
| Abort（默认） | 抛异常 |
| CallerRuns | 调用者执行（反压） |
| Lumina 选择 | 有界队列 + CallerRuns |
| 禁用 Executors | 无界队列 OOM 风险 |

---

📝 **本篇撰写期间修正的代码**：无。
