# C02 — @AgentTool 注解与反射注册

> **前置要求**：已完成 [C01-工具调用原理](C01-tool-calling-principle.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

上一篇你看到了 `@AgentTool` 注解。这节深入：怎么自己写一个工具？参数怎么自动提取？

---

## 写一个自定义工具

### 三步

```java
@Component                         // 1. 必须是 Spring Bean（才能被扫描到）
public class MyToolProvider {

    @AgentTool(                    // 2. 加注解
        name = "sms.send",         //    工具名（全局唯一）
        description = "发送短信通知。输入手机号和内容，返回发送结果。",  // 3. 描述（最重要！）
        category = "biz.sms"
    )
    public Map<String, Object> sendSms(String phone, String content) {
        // 方法参数自动变成工具的入参参数
        // 方法返回值自动变成工具的执行结果
        boolean success = smsService.send(phone, content);

        return Map.of(
            "success", success,
            "phone", phone,
            "timestamp", System.currentTimeMillis()
        );
    }
}
```

### 启动后自动注册

```
项目启动
  ↓
EnhancedToolManager.scanAndRegisterTools()
  ↓ 反射扫描所有 @Component Bean 的方法
  ↓ 发现 @AgentTool 注解的方法
  ↓ 提取 name/description/参数类型
  ↓ 注册为 ToolDefinition
  ↓
工具可用！Agent 执行时能调它
```

---

## 方法参数怎么变成工具参数

```java
@AgentTool(name = "sms.send", description = "...")
public Map<String, Object> sendSms(String phone, String content) {
```

框架用反射提取参数信息，自动生成 JSON Schema：

```json
{
  "name": "sms.send",
  "description": "发送短信通知...",
  "parameters": {
    "type": "object",
    "properties": {
      "phone": { "type": "string", "description": "手机号" },
      "content": { "type": "string", "description": "短信内容" }
    },
    "required": ["phone", "content"]
  }
}
```

LLM 看到这个 Schema，就知道"调 sms.send 要传 phone 和 content 两个字符串参数"。

---

## description 是灵魂

```java
// ❌ 差的 description——LLM 不知道什么时候用
@AgentTool(name = "sms.send", description = "发送短信")

// ✅ 好的 description——LLM 知道何时用、怎么用
@AgentTool(name = "sms.send",
    description = "发送短信通知。输入手机号和内容，返回发送结果。用于需要通知用户的场景。"
)
```

**description 写得好，LLM 就聪明；写得差，LLM 就不会用。** 这是工具开发最重要的功夫。

---

## 动手试试

1. 写一个自定义工具类（加 @AgentTool 注解的方法）
2. 重启项目
3. 在 Agent 配置里选这个工具
4. 问 Agent 相关问题，看它是否自动调用

---

## 小结

| 要点 | 记忆 |
|------|------|
| @Component + @AgentTool | 两个注解缺一不可 |
| 方法参数 | 自动变成工具入参（反射提取） |
| 方法返回值 | 自动变成工具结果 |
| description | 灵魂——决定 LLM 会不会正确使用 |

> 🚀 [C03 — 内置工具 →](C03-built-in-tools.md)

---

📝 **本篇撰写期间修正的代码**：无。
