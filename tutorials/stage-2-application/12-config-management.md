# 12 — 配置管理

> **前置要求**：已完成 [11-Nacos + Gateway](11-nacos-gateway.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

Lumina 的配置分散在好几个地方：`application.yml`、Profile、Nacos、环境变量。它们之间什么关系？优先级谁高？改配置该改哪里？

---

## 配置的四种来源（优先级从高到低）

```
① 环境变量（最高）     LLM_API_KEY=xxx
② 命令行参数           --server.port=9090
③ Nacos 配置中心       lumina-agent-service.yaml
④ application.yml      本地文件（最低）
```

**规则**：高优先级覆盖低优先级。同名配置，环境变量 > yml 文件。

> 💡 **实际建议**：
> - 密钥类（API Key、密码）→ 环境变量（不入 git）
> - 环境差异（端口、数据库地址）→ Profile + Nacos
> - 通用默认值 → application.yml

---

## application.yml 结构

```yaml
# 文件：lumina-standalone/src/main/resources/application.yml
spring:
  profiles:
    active: standalone                    # 当前 Profile

  datasource:                             # 数据库
    url: jdbc:mysql://localhost:3306/lumina_dev
    username: root
    password: ${MYSQL_PASSWORD:123456}    # ← 环境变量，默认 123456

  data:
    redis:
      host: ${REDIS_HOST:localhost}       # ← 环境变量，默认 localhost

server:
  port: 8080

lumina:                                   # Lumina 自定义配置
  jwt:
    secret-key: ${LUMINA_JWT_SECRET:dev-default-key}   # ← 环境变量
  agent:
    rate-limit:
      max-requests: 30
```

### `${环境变量:默认值}` 语法

```yaml
password: ${MYSQL_PASSWORD:123456}
```
- 有 `MYSQL_PASSWORD` 环境变量 → 用环境变量的值
- 没有 → 用默认值 `123456`

这是**密码不入 git 的标准做法**——代码里只写占位符，真实密码从环境变量注入。

---

## Profile：环境切换

### application-{profile}.yml

```
application.yml              ← 通用配置
application-standalone.yml   ← standalone 专属
application-test.yml         ← 测试环境专属
```

激活 standalone 时，两个文件合并（专属覆盖通用）。

### 切换 Profile

```bash
# 方式一：yml 里写
spring.profiles.active: standalone

# 方式二：启动参数
java -jar app.jar --spring.profiles.active=test

# 方式三：环境变量
export SPRING_PROFILES_ACTIVE=test
```

---

## 敏感配置不入 git

```bash
# .env.standalone（本地配置，已 gitignore）
LLM_API_KEY=sk-真实密钥
MYSQL_PASSWORD=真实密码
LUMINA_JWT_SECRET=真实密钥至少32字符

# .env.standalone.example（模板，入 git，不含真实值）
LLM_API_KEY=your-api-key-here
MYSQL_PASSWORD=your-password
```

---

## 小结

| 来源 | 优先级 | 适用 |
|------|--------|------|
| 环境变量 | 最高 | 密钥、环境差异 |
| 命令行参数 | 高 | 临时覆盖 |
| Nacos | 中 | 微服务模式集中管理 |
| application.yml | 低 | 默认值 |

> 🚀 [13 — 测试实践 →](13-testing-practice.md)

---

📝 **本篇撰写期间修正的代码**：无。
