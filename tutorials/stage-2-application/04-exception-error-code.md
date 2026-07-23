# 04 — 统一异常处理与错误码体系

> **前置要求**：已完成 [03-DTO/VO/Domain 模式](03-dto-vo-domain-pattern.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

如果没有统一异常处理，你写的每个接口都要这样：

```java
// ❌ 没有统一异常处理的世界
@PostMapping
public Map<String, Object> createAgent(@RequestBody Map<String, Object> body) {
    try {
        // 业务逻辑
    } catch (NameEmptyException e) {
        return Map.of("code", 400, "msg", "名称不能为空");
    } catch (DuplicateException e) {
        return Map.of("code", 409, "msg", "名称重复");
    } catch (SQLException e) {
        return Map.of("code", 500, "msg", "数据库错误");
    } catch (Exception e) {
        return Map.of("code", 500, "msg", "未知错误");
    }
}
// 每个接口都要写一遍 try-catch……
```

Lumina 用**统一异常处理**——业务代码只管 `throw`，框架统一捕获转成标准格式。这节讲清这套机制。

---

## 三件套：ErrorCode + BusinessException + GlobalExceptionHandler

### 1. ErrorCode 枚举——所有错误码的定义中心

```java
// 文件：lumina-common/.../core/ErrorCode.java
@Getter
public enum ErrorCode {

    // 通用
    SUCCESS(200, 200, "操作成功"),
    BAD_REQUEST(400, 400, "请求参数错误"),
    UNAUTHORIZED(401, 401, "未授权"),
    NOT_FOUND(404, 404, "资源不存在"),

    // 认证 1000-1999
    TOKEN_INVALID(401, 1001, "Token 无效或已过期"),
    LOGIN_FAILED(400, 1003, "用户名或密码错误"),

    // 用户 10000-10999
    USER_NOT_FOUND(404, 10001, "用户不存在"),

    // Agent 20000-20999
    AGENT_NOT_FOUND(404, 20001, "Agent 不存在"),
    AGENT_RATE_LIMITED(429, 20005, "Agent 调用频率超限"),

    ; // ... 更多

    // 每个错误码包含三个属性
    private final int httpStatus;    // HTTP 状态码（给前端判断成败）
    private final int code;          // 业务错误码（给前端精确区分）
    private final String message;    // 默认消息（给人看）
}
```

**分段规划**（注释里有说明）：

| 区间 | 模块 |
|------|------|
| 200-500 | 通用（和 HTTP 状态码一致） |
| 1000-1999 | 认证 |
| 10000-10999 | 用户 |
| 11000-11999 | 角色 |
| 20000-20999 | Agent |

> 💡 这种分段让你一眼看出错误码属于哪个模块——面试加分点。

### 2. BusinessException——业务里 throw 它

```java
// 文件：lumina-common/.../exception/BusinessException.java
public class BusinessException extends BaseException {

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage(), errorCode.getHttpStatus(), errorCode.getCode());
    }

    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage, errorCode.getHttpStatus(), errorCode.getCode());
    }

    // 静态工厂
    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    public static BusinessException notFound(String resource) {
        return new BusinessException(ErrorCode.NOT_FOUND, resource + " 不存在");
    }
}
```

**业务代码这样用**：

```java
// 文件：LlmProviderServiceImpl.java
public LlmProvider getById(Long id) {
    LlmProviderDO provider = providerMapper.selectById(id);
    if (provider == null) {
        throw new BusinessException(ErrorCode.LLM_PROVIDER_NOT_FOUND);  // ← 一行搞定
    }
    // ...
}
```

**不用 try-catch，直接 throw**——后面的 GlobalExceptionHandler 统一处理。

### 3. GlobalExceptionHandler——统一捕获转格式

```java
// 文件：lumina-framework/.../exception/GlobalExceptionHandler.java
@RestControllerAdvice                    // ← 全局异常处理器
public class GlobalExceptionHandler {

    // 最先匹配：BusinessException（业务异常）
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusiness(BusinessException e) {
        return R.fail(e.getCode(), e.getErrCode(), e.getMessage());
        // 返回：{ code: 404, errCode: 20001, msg: "Agent 不存在" }
    }

    // 参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<List<ValidationError>> handleValidation(MethodArgumentNotValidException e) {
        // 返回校验错误详情
    }

    // 兜底：所有其他异常
    @ExceptionHandler(Exception.class)
    public R<Void> handleAll(Exception e) {
        log.error("未处理异常", e);
        return R.fail(500, "系统内部错误");
    }
}
```

### 异常处理的优先级链

```
抛出异常
  ↓
GlobalExceptionHandler 按从具体到抽象匹配：
  1. BusinessException        → 业务异常（你主动 throw 的）
  2. 参数校验异常              → @Valid 不通过的
  3. 其他已知异常              → 如 SQL 异常
  4. Exception（兜底）         → 所有未知异常
  ↓
统一返回 R<Void> 格式
```

---

## 前端怎么配合

```typescript
// 文件：lumina-frontend/src/api/request.ts（响应拦截器）
service.interceptors.response.use((response) => {
  const res = response.data

  if (res.code === 200) {
    return res    // 成功
  } else {
    ElMessage.error(res.msg || '请求失败')    // ← 统一弹错误提示

    if (res.code === 401) {
      removeToken()
      window.location.href = '/login'    // 401 跳登录
    }

    return Promise.reject(new Error(res.msg))
  }
})
```

**效果**：后端抛 `BusinessException(ErrorCode.AGENT_NOT_FOUND)` → 前端自动弹出"Agent 不存在"提示。**你不用在前端写任何错误处理代码**。

---

## 统一响应格式 R<T>

```java
// 文件：lumina-common/.../core/R.java
{
  "code": 200,          // HTTP 状态码（200=成功，4xx/5xx=失败）
  "errCode": 0,         // 业务错误码（成功=0，失败=ErrorCode.code）
  "msg": "操作成功",     // 消息
  "data": { ... },      // 数据
  "timestamp": 1234567  // 时间戳
}
```

| 字段 | 值 | 前端怎么用 |
|------|----|----------|
| `code` | 200 / 404 / 500 | 判断成功还是失败 |
| `errCode` | 0 / 20001 / 20005 | 精确区分业务场景（Agent 不存在 vs 频率超限） |
| `msg` | "Agent 不存在" | 直接弹给用户看 |
| `data` | AgentVO | 成功时拿数据 |

---

## 为什么这么设计（面试常问）

### Q：为什么不直接用 HTTP 状态码？

**A**：HTTP 状态码太粗了。404 只能说"找不到"，但分不清是"Agent 找不到"还是"用户找不到"。`errCode`（20001 vs 10001）做精确区分。

### Q：为什么 `@RestControllerAdvice` 能自动捕获？

**A**：AOP。Spring 给所有 Controller 方法包了代理，方法抛异常时代理拦截，转发到 `@ExceptionHandler` 方法。详见[第三阶 03-AOP 代理](../stage-3-mastery/03-spring-aop-proxy.md)。

### Q：兜底的 `Exception` 为什么要 log.error？

**A**：未知异常是 bug——必须记录完整堆栈方便排查。已知的 `BusinessException` 是正常业务流程，只记 debug 级别。

---

## 动手试试

1. **打开 `ErrorCode.java`**：数数有多少个错误码，看看分段规划
2. **在项目里搜索 `throw new BusinessException`**：看看哪些地方在抛业务异常
3. **打开 `GlobalExceptionHandler.java`**：看看有多少个 `@ExceptionHandler`
4. **故意触发一个错误**：创建 Agent 时不填名称，看前端弹出什么提示

---

## 小结

| 组件 | 职责 | 一句话记忆 |
|------|------|-----------|
| ErrorCode | 定义所有错误码 | 分段枚举，每个含 httpStatus+code+message |
| BusinessException | 业务里 throw | `throw new BusinessException(ErrorCode.XXX)` 一行搞定 |
| GlobalExceptionHandler | 统一捕获转格式 | @RestControllerAdvice，从具体到抽象匹配 |
| R<T> | 统一响应格式 | code 判断成败，errCode 精确区分，msg 给人看 |

**核心价值**：业务代码只管 throw，异常处理统一做。前端统一接收、统一弹提示。

---

## 下一步

下一篇 [校验与审计](05-validation-and-audit.md)——`@Valid` 参数校验和 `@Audit` 审计日志的 AOP 实现。

> 🚀 [05 — 校验与审计 →](05-validation-and-audit.md)

---

## 自测题

1. **`ErrorCode` 为什么要有 `httpStatus` 和 `code` 两个码？用一个不行吗？**
   <details><summary>答案</summary>httpStatus 太粗（404 分不清什么找不到），code 做精确区分（20001=Agent 不存在，10001=用户不存在）。httpStatus 给前端判断成败，code 给前端精确处理业务场景。</details>

2. **为什么 GlobalExceptionHandler 的 @ExceptionHandler 要从具体到抽象排列？**
   <details><summary>答案</summary>Spring 按继承关系匹配——先匹配最具体的（BusinessException），匹配不到才找父类。如果 Exception（兜底）放前面，所有异常都会被它捕获，具体的处理器失效。</details>

3. **业务代码里 throw BusinessException 后，调用方需要 try-catch 吗？**
   <details><summary>答案</summary>不需要。GlobalExceptionHandler 统一捕获处理。业务代码直接 throw，不用层层 try-catch。</details>

---

📝 **本篇撰写期间修正的代码**：无。
