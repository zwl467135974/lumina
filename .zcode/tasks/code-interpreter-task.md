# Code Interpreter 实施任务

## 参考文件（先读）
- `lumina-agent-core/src/main/java/io/lumina/agent/tool/GeneralToolProvider.java` — @AgentTool 用法、返回 Map 模式、错误处理
- `lumina-agent-core/src/main/java/io/lumina/agent/tool/AgentTool.java` — 注解定义
- `lumina-agent-core/src/main/java/io/lumina/agent/config/LuminaAgentProperties.java` — 配置类结构

## 文件1：创建 CodeInterpreterToolProvider.java
路径: `lumina-agent-core/src/main/java/io/lumina/agent/tool/CodeInterpreterToolProvider.java`

- 包名 io.lumina.agent.tool
- @Component + @ConditionalOnProperty(prefix="lumina.agent.code-interpreter", name="enabled", havingValue="true")
- @Slf4j
- 方法 @AgentTool(name="code.execute", description="执行代码并返回输出结果。支持 python 和 javascript 语言。用于数据分析、计算等。", category="code.interpreter")
- 签名: public Map<String, Object> execute(String language, String code)
- @Value 注入字段:
  - timeoutSeconds: ${lumina.agent.code-interpreter.timeout-seconds:30}
  - maxOutputLength: ${lumina.agent.code-interpreter.max-output-length:10000}
  - pythonPath: ${lumina.agent.code-interpreter.python-path:python3}
  - nodePath: ${lumina.agent.code-interpreter.node-path:node}
  - workDir: #{T(java.lang.System).getProperty('java.io.tmpdir') + '/lumina-code'}
- 执行逻辑:
  1. 校验 language: 只允许 "python" 和 "javascript"，否则返回 {success:false, error:"不支持的语言，仅支持 python/javascript"}
  2. 校验 code 非空: null 或 blank 返回 {success:false, error:"代码不能为空"}
  3. Files.createDirectories(workDir)
  4. 写临时文件: python→.py, javascript→.js，文件名 "lumina_code_" + System.nanoTime() + 后缀
  5. ProcessBuilder: 命令=[pythonPath/nodePath, 脚本路径], redirectErrorStream(true), directory=Path.of(workDir).toFile()
  6. process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
  7. 超时: process.destroyForcibly(), 返回 {success:false, error:"执行超时(" + timeoutSeconds + "s)"}
  8. 读 stdout: process.getInputStream(), 截断到 maxOutputLength 字符
  9. 返回 {success:(exitCode==0), stdout, exitCode, durationMs}
  10. 异常 catch: 返回 {success:false, error:e.getMessage()}
  11. finally: Files.deleteIfExists(脚本文件)
- import: java.util.Map, java.util.HashMap, java.nio.file.*, java.util.concurrent.TimeUnit, io.lumina.agent.tool.AgentTool

## 文件2：修改 LuminaAgentProperties.java
在 memory 字段后面加:
```java
private CodeInterpreterConfig codeInterpreter = new CodeInterpreterConfig();
```
在 MemoryConfig 内部类之后加:
```java
@Data
public static class CodeInterpreterConfig {
    private boolean enabled = false;
    private int timeoutSeconds = 30;
    private int maxOutputLength = 10000;
    private String pythonPath = "python3";
    private String nodePath = "node";
    private String workDir = System.getProperty("java.io.tmpdir") + "/lumina-code";
}
```

## 文件3：创建测试
路径: `lumina-agent-core/src/test/java/io/lumina/agent/tool/CodeInterpreterToolProviderTest.java`
- @ExtendWith(MockitoExtension.class)
- 创建 CodeInterpreterToolProvider 实例，用 ReflectionTestUtils 设置 @Value 字段 (timeoutSeconds=30, maxOutputLength=10000, pythonPath="python3", nodePath="node", workDir=tmpdir+"/lumina-code-test")
- 测试用例:
  1. execute("ruby", "puts 1") → success==false, error 包含 "不支持"
  2. execute("python", null) → success==false, error 包含 "空"
  3. execute("python", "  ") → success==false, error 包含 "空"

## 文件4：修改 Nacos 配置
文件: `nacos-config/lumina-agent-service.yaml`
在 agent: 块内, memory: 块之后, llm: 块之前插入:
```yaml
    code-interpreter:
      enabled: ${CODE_INTERPRETER_ENABLED:false}
      timeout-seconds: ${CODE_INTERPRETER_TIMEOUT:30}
      max-output-length: ${CODE_INTERPRETER_MAX_OUTPUT:10000}
      python-path: ${CODE_INTERPRETER_PYTHON:python3}
      node-path: ${CODE_INTERPRETER_NODE:node}
```

## 验证步骤
1. mvn compile -pl lumina-agent-core -q
2. mvn test -pl lumina-agent-core -am -Dtest="CodeInterpreterToolProviderTest" -Dsurefire.failIfNoSpecifiedTests=false
3. 有错就修到通过
