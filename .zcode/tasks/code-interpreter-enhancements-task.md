# Code Interpreter 四项增强任务

## 概述
在现有 CodeInterpreterToolProvider 基础上做 4 项增强：
1. pip install 支持（运行时安装依赖）
2. 容器池（常驻容器复用）
3. 流式输出（长任务实时输出）
4. 多文件项目（requirements.txt + 多脚本）

所有改动集中在 CodeInterpreterToolProvider.java + LuminaAgentProperties.java + Nacos 配置。

## 参考文件（先读）
- `lumina-agent-core/src/main/java/io/lumina/agent/tool/CodeInterpreterToolProvider.java` — 当前完整代码
- `lumina-agent-core/src/main/java/io/lumina/agent/config/LuminaAgentProperties.java` — CodeInterpreterConfig 在 195 行
- `nacos-config/lumina-agent-service.yaml` — code-interpreter 配置块

---

## 改动 1：LuminaAgentProperties.CodeInterpreterConfig 添加新字段

在 CodeInterpreterConfig 内部类（约 237 行 networkDisabled 之后）添加：

```java
/** 运行时依赖安装（pip install / npm install），执行前先安装 */
private boolean autoInstallDeps = false;

/** 容器池大小（常驻容器复用，0=不启用容器池每次新建） */
private int poolSize = 2;

/** 容器空闲超时（分钟，超过自动销毁） */
private int poolIdleTimeoutMinutes = 10;
```

---

## 改动 2：重写 CodeInterpreterToolProvider.java

### 核心改动点

#### 2a. 新增容器池 ContainerPool 内部类

在 CodeInterpreterToolProvider 内部创建一个静态内部类管理容器复用：

```java
/**
 * 容器池：按 (image) 维度缓存常驻容器，空闲超时自动销毁
 */
private static class ContainerPool {
    private final int maxSize;
    private final int idleTimeoutMinutes;
    // key = image name, value = list of pooled containers
    private final Map<String, BlockingQueue<PooledContainer>> pools = new ConcurrentHashMap<>();

    // PooledContainer 包装 GenericContainer + lastUsedTime
    // borrowContainer(image, config) → 从池中取或新建
    // returnContainer(container) → 归还到池（更新 lastUsedTime）
    // cleanupIdle() → 销毁超时空闲容器（可选，由 @Scheduled 或 borrow 时触发）
}
```

关键行为：
- `borrowContainer(image)`：池里有空闲容器就取出，没有就新建一个 sleep 保活容器
- `returnContainer(container)`：用完归还，更新最后使用时间
- poolSize=0 时不启用容器池，走原来的每次新建逻辑
- 容器创建参数（内存/CPU/网络）和现有 docker 模式一致

#### 2b. executeInDocker 改用容器池

```java
private Map<String, Object> executeInDocker(String lang, Path scriptFile, Map<String, Object> result) {
    // 1. 从容器池借容器（或新建）
    PooledContainer pooled = containerPool.borrowContainer(image, ...config...);
    GenericContainer<?> container = pooled.getContainer();

    try {
        // 2. 如果 autoInstallDeps=true，检查 /code 下有没有 requirements.txt（python）或 package.json（node）
        //    有就在容器内执行 pip install -r /code/requirements.txt 或 npm install
        if (autoInstallDeps) {
            installDepsInContainer(container, lang, scriptFile.getParent());
        }

        // 3. 在容器内执行脚本
        ExecResult execResult = execInContainerWithTimeout(container, cmd, containerScriptPath, timeoutSeconds);

        // 4. 返回结果（和现有逻辑一致）
    } finally {
        // 5. 归还容器到池（而不是 stop）
        containerPool.returnContainer(pooled);
    }
}
```

#### 2c. 依赖安装 installDepsInContainer

```java
private void installDepsInContainer(GenericContainer<?> container, String lang, Path codeDir) {
    if ("python".equals(lang)) {
        Path reqFile = codeDir.resolve("requirements.txt");
        if (Files.exists(reqFile)) {
            container.execInContainer("pip", "install", "-r", "/code/requirements.txt", "-q");
        }
    } else if ("javascript".equals(lang)) {
        Path pkgFile = codeDir.resolve("package.json");
        if (Files.exists(pkgFile)) {
            container.execInContainer("npm", "install", "--prefix", "/code");
        }
    }
}
```

注意：autoInstallDeps=true 时需要临时放开网络（pip/npm 需要下载），安装完后恢复。实现方式：
- 创建容器时 networkDisabled 按配置来
- 如果 autoInstallDeps=true 且 networkDisabled=true，安装阶段临时用 `container.execInContainer` 时 Docker 会允许容器内网络（因为 execInContainer 不受 createCmd 的 networkDisabled 影响——实际上 networkDisabled 是容器级别的，exec 内也不行）

正确方案：autoInstallDeps=true 时，容器创建时 networkDisabled 设为 false（安装需要网络），安装完后再禁用。但 Testcontainers 不支持运行时改网络。

最终方案（简单可靠）：autoInstallDeps=true 时，networkDisabled 强制为 false（因为需要下载依赖）。在日志中 warn 提示。

#### 2d. 流式输出 execInContainerWithTimeout

现有 execInContainer 是一次性等待完成。改为支持流式回调：

```java
/**
 * 在容器内执行命令，支持超时和流式输出回调
 */
private Container.ExecResult execInContainerWithTimeout(
        GenericContainer<?> container, long timeoutSeconds,
        Consumer<String> onOutput, String... command) throws Exception {

    // 用 CompletableFuture 异步执行
    CompletableFuture<Container.ExecResult> future = CompletableFuture.supplyAsync(() -> {
        return container.execInContainer(command);
    });

    // 超时控制（和现有逻辑一致）
    try {
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException te) {
        future.cancel(true);
        throw te;
    }
}
```

真正的流式输出需要用 Docker logs API 的 follow 模式。但 Testcontainers 的 execInContainer 不支持流式回调。

**简化方案（推荐）**：在 @AgentTool 方法层面不改返回类型（仍然是 Map），但在 execute 方法的 result 中加一个 `streamed: false` 标记。真正的流式输出（SSE 推到前端）不在这次做，而是在 Agent 执行层面（StreamChunk）处理。

所以 #3 流式输出的实现变为：
- 在 execute() 返回的 Map 中增加 `partialOutput` 字段（如果输出很长，分段返回）
- 实际上当前的一次性返回已经够用，真正需要流式的场景（比如训练模型）可以后续在 Agent 层面做

**#3 最终实现**：暂时只在配置里预留 `streamOutput` 字段（默认 false），代码里加注释说明后续实现方向。不改变现有执行逻辑。这样避免过度设计。

#### 2e. 多文件项目支持

修改 execute() 入口，增加一个重载方法：

```java
@AgentTool(
    name = "code.execute",
    description = "执行代码并返回输出结果。支持 python 和 javascript。用于数据分析、计算等。",
    category = "code.interpreter"
)
public Map<String, Object> execute(String language, String code) {
    // 现有逻辑不变，单文件执行
}
```

不需要新增 @AgentTool 方法。多文件支持通过 `autoInstallDeps` + requirements.txt 实现：
- 如果 code 参数里包含 `# requirements: pandas,numpy` 这样的注释行
- 或者 code 本身写 `import pandas`，但 pandas 没装
- autoInstallDeps=true 时自动处理

实际实现：在写临时文件前，检查 code 内容里有没有特殊标记：
```python
# 检查代码中是否有依赖声明
# 格式：# pip: pandas,numpy,matplotlib 或 # npm: lodash,axios
```
如果有，在容器内先安装这些包。

---

## 改动 3：Nacos 配置

在 nacos-config/lumina-agent-service.yaml 的 code-interpreter 块中添加：
```yaml
      auto-install-deps: ${CODE_INTERPRETER_AUTO_INSTALL:false}
      pool-size: ${CODE_INTERPRETER_POOL_SIZE:2}
      pool-idle-timeout-minutes: ${CODE_INTERPRETER_POOL_IDLE_TIMEOUT:10}
```

---

## 改动 4：更新现有测试

文件：`lumina-agent-core/src/test/java/io/lumina/agent/tool/CodeInterpreterToolProviderTest.java`
在 setUp() 中设置新 @Value 字段：
- autoInstallDeps = false
- poolSize = 0（测试时不启用容器池）
- poolIdleTimeoutMinutes = 10

文件：`lumina-agent-core/src/test/java/io/lumina/agent/tool/CodeInterpreterToolProviderExecTest.java`
同上设置新字段。

---

## 改动 5：新增测试（如果有必要）

不需要新增测试文件。现有的 6 个测试（3 单元 + 3 执行）覆盖核心逻辑。
新功能（容器池/依赖安装）的实际执行测试在验收阶段由 ZCode 单独写。

---

## 验证步骤
1. mvn compile -pl lumina-agent-core -q
2. mvn test -pl lumina-agent-core -am -Dtest="CodeInterpreterToolProviderTest,CodeInterpreterToolProviderExecTest" -Dsurefire.failIfNoSpecifiedTests=false
3. 有错就修到通过

## 重要约束
- @AgentTool 注解和 execute(String, String) 方法签名不能变（不影响 Agent 调用）
- poolSize=0 时走原有逻辑（不启用容器池），保证向后兼容
- autoInstallDeps=false 时跳过依赖安装，行为和现在完全一致
- 容器池是线程安全的（BlockingQueue + ConcurrentHashMap）
- 容器池归还时不需要清理容器内状态（简单版，每次 execInContainer 是独立进程）
- Testcontainers 的 GenericContainer 复用时注意：execInContainer 可以多次调用，容器保持运行
