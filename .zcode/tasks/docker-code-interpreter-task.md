# Docker 版 Code Interpreter 升级任务

## 目标
将 CodeInterpreterToolProvider 从 ProcessBuilder 升级为 Docker 容器隔离执行，保留 ProcessBuilder 作为 fallback（Docker 不可用时降级）。

## 参考文件（先读）
- `lumina-agent-core/src/main/java/io/lumina/agent/tool/CodeInterpreterToolProvider.java` — 现有 ProcessBuilder 版（要修改）
- `lumina-agent-core/src/main/java/io/lumina/agent/config/LuminaAgentProperties.java` — 配置类（要修改）
- `lumina-agent-core/pom.xml` — 依赖（要修改）
- `pom.xml` — root pom（已有 testcontainers BOM 1.20.6）
- `lumina-agent-core/src/main/java/io/lumina/agent/tool/GeneralToolProvider.java` — 工具模式参考

## 文件1：修改 lumina-agent-core/pom.xml
在现有依赖中添加 Testcontainers（从 test 提升到 main scope）：
```xml
<!-- Docker 容器隔离（Code Interpreter 沙箱） -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>compile</scope>
</dependency>
```
注意：版本由 root pom 的 testcontainers-bom 1.20.6 管理，不需要指定 version。

## 文件2：修改 LuminaAgentProperties.java
在 CodeInterpreterConfig 内部类中添加 Docker 相关字段：
```java
/**
 * 执行模式：process（本地进程）或 docker（Docker 容器隔离）
 */
private String mode = "process";

/**
 * Docker 镜像（Python）
 */
private String pythonImage = "python:3.11-slim";

/**
 * Docker 镜像（Node.js）
 */
private String nodeImage = "node:20-slim";

/**
 * Docker 内存限制（MB）
 */
private int memoryLimitMb = 256;

/**
 * Docker CPU 核心数限制
 */
private double cpuLimit = 1.0;

/**
 * 是否禁止网络访问
 */
private boolean networkDisabled = true;
```

## 文件3：重写 CodeInterpreterToolProvider.java
路径: `lumina-agent-core/src/main/java/io/lumina/agent/tool/CodeInterpreterToolProvider.java`

核心改动：根据 config.mode 选择执行方式（docker 或 process）。

逻辑：
1. 校验语言白名单（不变）
2. 校验代码非空（不变）
3. 写代码到临时文件（不变）
4. **新增**：检查 mode
   - mode == "docker" 且 Docker 可用 → 调 executeInDocker()
   - 否则 → 调 executeInProcess()（原有逻辑）
5. **executeInDocker()** 实现：
   ```java
   private Map<String, Object> executeInDocker(String lang, Path scriptFile) {
       String image = "python".equals(lang) ? pythonImage : nodeImage;
       String cmd = "python".equals(lang) ? "python3" : "node";

       // 创建容器：挂载脚本文件为只读，限制资源，禁止网络
       GenericContainer<?> container = new GenericContainer<>(image)
           .withFileSystemBind(scriptFile.getParent().toString(), "/code", BindMode.READ_ONLY)
           .withCommand(cmd, "/code/" + scriptFile.getFileName().toString())
           .withStartupTimeout(Duration.ofSeconds(30));

       // 资源限制
       ((org.testcontainers.containers.GenericContainer<?>) container)
           .withCreateContainerCmdModifier(cmdBuilder -> {
               cmdBuilder.withMemory((long) memoryLimitMb * 1024 * 1024);
               // CPU 限制通过 NanoCpus 设置
               cmdBuilder.withNanoCpus((long)(cpuLimit * 1_000_000_000));
           });

       // 网络隔离
       if (networkDisabled) {
           ((org.testcontainers.containers.GenericContainer<?>) container)
               .withNetworkDisabled();
       }

       container.start();
       // 等待执行完成
       ExecResult result = container.execInContainer(cmd, "/code/" + scriptFile.getFileName().toString());
       // 或者用 container 等待退出
   ```

   实际实现注意：
   - 用 `withCommand` 设置容器启动命令
   - 用 `container.start()` 启动
   - 用 `container.waitForLog()` 或检查 `container.getCurrentContainerInfo().getState().getExitCode()` 获取结果
   - 用 `container.getLogs()` 获取 stdout/stderr
   - finally 中 `container.stop()` 清理容器

   更简单可靠的方式：用 `container.execInContainer()` 在已启动的容器内执行：
   ```java
   container.start();
   ExecResult execResult = container.execInContainer(
       TimeUnit.SECONDS.toMillis(timeoutSeconds),
       cmd, "/code/" + scriptFile.getFileName().toString()
   );
   String stdout = execResult.getStdout();
   int exitCode = execResult.getExitCode();
   ```
   但 execInContainer 的超时参数是可选的，需要用 `container.execInContainer(cmd, args...)` 然后自己控制超时。

   **推荐方案（最可靠）**：直接用 withCommand 启动容器执行脚本，容器退出后获取 logs：
   ```java
   GenericContainer<?> container = new GenericContainer<>(image)
       .withFileSystemBind(scriptFile.getParent().toString(), "/code", BindMode.READ_ONLY)
       .withCommand(cmd, "/code/" + scriptFile.getFileName().toString());

   container.start();

   // 等待容器退出（带超时）
   try {
       waiting for container to finish...
   }
   ```

   **最终推荐实现**（经过分析最可靠的方式）：
   ```java
   private Map<String, Object> executeInDocker(String lang, Path scriptFile, Map<String, Object> result) {
       String image = "python".equals(lang) ? pythonImage : nodeImage;
       String cmd = "python".equals(lang) ? "python3" : "node";
       String containerScriptPath = "/code/" + scriptFile.getFileName().toString();

       try (GenericContainer<?> container = new GenericContainer<>(image)
               .withFileSystemBind(scriptFile.getParent().toString(), "/code", BindMode.READ_WRITE)
               .withCommand(cmd, containerScriptPath)
               .withStartupTimeout(Duration.ofSeconds(timeoutSeconds))) {

           // 资源限制
           container.withCreateContainerCmdModifier(createCmd -> {
               createCmd.withMemory((long) memoryLimitMb * 1024 * 1024);
               createCmd.withNanoCpus((long)(cpuLimit * 1_000_000_000L));
               if (networkDisabled) {
                   createCmd.withNetworkDisabled(true);
               }
           });

           long startNanos = System.nanoTime();
           container.start();

           // 等待容器执行完毕
           ContainerState state = container;
           // container.start() 会等待 WaitStrategy（默认是日志匹配）
           // 对于执行脚本退出的场景，需要用自定义 WaitStrategy 或直接等待
           // 最简单：等待容器停止
           while (container.isRunning()) {
               Thread.sleep(100);
               if ((System.nanoTime() - startNanos) / 1_000_000_000 > timeoutSeconds) {
                   container.stop();
                   result.put("success", false);
                   result.put("error", "执行超时");
                   return result;
               }
           }

           String logs = container.getLogs();
           // 截断
           if (logs.length() > maxOutputLength) {
               logs = logs.substring(0, maxOutputLength) + "...(已截断)";
           }

           long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
           result.put("success", true); // 日志中无错误即为成功（简化）
           result.put("stdout", logs);
           result.put("durationMs", durationMs);
           return result;

       } catch (Exception e) {
           result.put("success", false);
           result.put("error", "Docker 执行失败: " + e.getMessage());
           return result;
       }
   }
   ```

   注意：
   - 用 try-with-resources 确保 container.stop() 被调用（GenericContainer 实现了 AutoCloseable）
   - 首次执行会拉取镜像（python:3.11-slim ~50MB），可能需要 1-2 分钟
   - `withFileSystemBind` 挂载脚本目录到容器 /code（READ_WRITE 因为有些脚本可能需要写临时文件）
   - 默认 WaitStrategy 是日志匹配 `.*`，对于命令行脚本需要改或用循环等待

## 文件4：修改 Nacos 配置
文件: `nacos-config/lumina-agent-service.yaml`
在现有 code-interpreter 块中补充：
```yaml
    code-interpreter:
      enabled: ${CODE_INTERPRETER_ENABLED:false}
      mode: ${CODE_INTERPRETER_MODE:process}           # process 或 docker
      timeout-seconds: ${CODE_INTERPRETER_TIMEOUT:30}
      max-output-length: ${CODE_INTERPRETER_MAX_OUTPUT:10000}
      python-path: ${CODE_INTERPRETER_PYTHON:python3}
      node-path: ${CODE_INTERPRETER_NODE:node}
      python-image: ${CODE_INTERPRETER_PYTHON_IMAGE:python:3.11-slim}
      node-image: ${CODE_INTERPRETER_NODE_IMAGE:node:20-slim}
      memory-limit-mb: ${CODE_INTERPRETER_MEMORY:256}
      cpu-limit: ${CODE_INTERPRETER_CPU:1.0}
      network-disabled: ${CODE_INTERPRETER_NO_NETWORK:true}
```

## 文件5：更新现有测试
路径: `lumina-agent-core/src/test/java/io/lumina/agent/tool/CodeInterpreterToolProviderTest.java`
在 setUp() 中额外设置新 @Value 字段：
- mode = "process"（测试用 process 模式，不依赖 Docker）
- pythonImage, nodeImage, memoryLimitMb, cpuLimit, networkDisabled

## 验证步骤
1. mvn compile -pl lumina-agent-core -q
2. mvn test -pl lumina-agent-core -am -Dtest="CodeInterpreterToolProviderTest,CodeInterpreterToolProviderExecTest" -Dsurefire.failIfNoSpecifiedTests=false
3. 有错就修到通过

## 注意事项
- Testcontainers 依赖从 test scope 提升到 compile scope
- Docker 不可用时自动降级到 ProcessBuilder（在 execute() 方法开头检查）
- 检查 Docker 可用性：用 `org.testcontainers.DockerClientFactory.instance().isDockerAvailable()`（如果有这个方法），或者 try-catch container.start() 失败就降级
- 保持 @AgentTool 注解和方法签名完全不变（不影响 Agent 调用）
