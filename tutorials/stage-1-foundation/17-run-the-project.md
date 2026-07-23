# 17 — 把项目完整跑起来

> **前置要求**：已完成 [01-16 全部章节](README.md)
> **预计阅读**：15 分钟
> **难度**：⭐☆☆☆☆

---

## 这节解决什么问题

你已经学完了第一阶所有基础概念。现在到了最激动的时刻——**亲手把 Lumina 项目跑起来**，用浏览器打开看到登录页，输入账号密码登录，看到 Agent 管理界面。

这节是第一阶的毕业考核——如果你能跑起来并成功登录，说明前面的基础都掌握了。

---

## 启动方式：standalone 模式（最简）

### 为什么用 standalone？

第一阶推荐用 **standalone 单体模式**——只需 MySQL + Redis，一条命令启动。不用搞 Nacos、RocketMQ、三个微服务进程。等你熟练了再试微服务模式。

### 第一步：确保 MySQL 和 Redis 在运行

```bash
# 检查 Docker 容器（01 篇里启动的）
docker ps
# 应该能看到 lumina-mysql 和 lumina-redis 在运行

# 如果没运行，重新启动
docker start lumina-mysql lumina-redis
```

### 第二步：设置 LLM API Key

```bash
# Linux/Mac
export LLM_API_KEY=sk-你的-API-Key

# Windows PowerShell
$env:LLM_API_KEY="sk-你的-API-Key"
```

### 第三步：构建 standalone jar

```bash
# 在项目根目录
mvn -pl lumina-standalone -am package -DskipTests
```

`-pl lumina-standalone` = 只构建 standalone 模块
`-am` = 同时构建它依赖的模块（common/framework/agent-core/business-base 等）

第一次构建会下载依赖，可能要几分钟（配了阿里云镜像应该很快）。

构建成功后，jar 包在：
```
lumina-standalone/target/lumina-standalone-1.0.0-SNAPSHOT.jar
```

### 第四步：启动后端

```bash
# 用环境变量覆盖 LLM Key
java -jar lumina-standalone/target/lumina-standalone-1.0.0-SNAPSHOT.jar
```

看到日志出现：
```
Started LuminaStandaloneApplication in XX seconds
```
说明启动成功！

> 💡 如果报 `Flyway migration failed`，检查 MySQL 是否在运行、密码是否是 123456。

### 第五步：验证后端

打开浏览器访问：
```
http://localhost:8080/actuator/health
```

应该看到：
```json
{ "status": "UP" }
```

### 第六步：测试登录 API

```bash
curl -X POST http://localhost:8080/api/v1/base/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

应该返回包含 `token` 的 JSON。**说明后端完全正常！**

---

## 启动前端

### 第一步：安装依赖

```bash
cd lumina-frontend
pnpm install
```

第一次会下载依赖，稍等几分钟。

### 第二步：启动开发服务器

```bash
pnpm dev
```

看到输出：
```
  VITE vX.X.X  ready in XXX ms
  ➜  Local:   http://localhost:3000/
```

### 第三步：打开浏览器

访问 **http://localhost:3000**

你应该看到 Lumina 登录页面！

---

## 登录验证

输入默认账号：
- 用户名：`admin`
- 密码：`admin123`

点击登录——应该进入 Dashboard 首页，看到：
- 统计卡片（Agent 数量、任务数量等）
- 侧边菜单（Agent 管理、系统管理、知识库...）
- 顶栏（主题切换、语言切换、用户头像）

**🎉 恭喜！你成功跑起了 Lumina 项目！**

---

## 试试核心功能

### 1. 查看 Agent 列表

点击侧边菜单 **Agent 管理**——看到 Agent 列表（如果有种子数据应该有示例 Agent）。

### 2. 查看数据库

```bash
docker exec -it lumina-mysql mysql -uroot -p123456 lumina_dev -e "SHOW TABLES;"
```

你应该看到 40+ 张表——这些都是 Flyway 自动建的。

### 3. 查看审计日志

登录后点击 **系统管理 → 审计日志**——你刚才的登录操作已经被 `@Audit` 自动记录了！

### 4. 切换暗色主题

点击顶栏的月亮图标——整个界面切换到暗色主题。这就是 CSS 变量 + Pinia store 的效果。

---

## 常见问题排查

### Q: 启动报"Communications link failure"
**A**: MySQL 没启动或连不上。检查 `docker ps` 看 MySQL 是否在运行，检查端口 3306。

### Q: 启动报 "Flyway migration failed"
**A**: 可能是数据库密码不对。Lumina 默认密码是 `123456`。如果改过，编辑 `lumina-standalone/src/main/resources/application.yml` 里的 `spring.datasource.password`。

### Q: 前端登录提示 "Network Error"
**A**: 后端没启动或端口不对。检查 `http://localhost:8080/actuator/health` 能否访问。检查 `vite.config.ts` 的 proxy target 是否是 `localhost:8080`。

### Q: 前端页面空白，控制台报跨域错误
**A**: 确认你通过 `localhost:3000` 访问（不是直接打开 .vue 文件）。Vite proxy 会自动处理跨域。

### Q: 端口被占用
**A**: 改配置。后端改 `application.yml` 的 `server.port`，前端改 `vite.config.ts` 的 `server.port`。

---

## 用操作脚本（更方便）

Lumina 提供了便捷脚本（适合反复启停调试）：

```bash
# 配置环境
cp .env.standalone.example .env.standalone
# 编辑 .env.standalone，填入 LLM_API_KEY

# 常用命令
./scripts/standalone.sh build     # 构建 jar（首次必跑）
./scripts/standalone.sh start     # 后台启动
./scripts/standalone.sh status    # 查看状态
./scripts/standalone.sh logs      # 实时查日志（Ctrl+C 退出）
./scripts/standalone.sh stop      # 停止
./scripts/standalone.sh restart   # 重启
```

---

## 🎓 第一阶毕业！

你已经完成了第一阶的全部 17 篇教程，并且成功把项目跑起来了！

### 回顾你学到了什么

**后端基础**：
- ✅ Maven 多模块项目管理
- ✅ Spring Boot（IoC/DI/AOP/自动配置）
- ✅ MyBatis-Plus（无 SQL 的 CRUD + 多租户）
- ✅ Redis（缓存/分布式锁/限流）
- ✅ Flyway（数据库版本管理）

**前端基础**：
- ✅ Vue 3 Composition API
- ✅ Element Plus 组件库
- ✅ Pinia 状态管理 + Vue Router
- ✅ TypeScript + Vite
- ✅ Axios 封装 + SSE 流式

**实操**：
- ✅ 把项目完整跑起来了
- ✅ 用浏览器访问并登录
- ✅ 体验了核心功能

---

## 下一步去哪

你现在有三个选择：

1. **[第二阶：项目实战应用](../stage-2-application/README.md)** — 理解设计理念，学会开发新功能（推荐）
2. **[AI 专项 A 模块](../stage-4-ai-agent/A01-llm-fundamentals.md)** — 如果你对 AI 更好奇，先了解什么是大模型
3. **复习第一阶** — 如果觉得还不熟，回头多看几篇

> 🚀 **推荐路径**：进入[第二阶](../stage-2-application/README.md)，从 [一个请求的旅程](../stage-2-application/01-request-lifecycle.md) 开始——把第一阶学的所有技术串起来。

---

## 自测题

1. **standalone 模式和微服务模式的区别是什么？**
   <details><summary>答案</summary>standalone 是单体模式（一个进程，只需 MySQL+Redis）；微服务是三服务模式（Gateway+Base+Agent，需要 Nacos）。standalone 适合开发体验，微服务适合生产。</details>

2. **`mvn -pl lumina-standalone -am package` 中 `-am` 的作用？**
   <details><summary>答案</summary>also-make，自动先构建 standalone 依赖的所有模块（common/framework/agent-core/business-base/notification）。不加 -am 会报找不到依赖。</details>

3. **前端开发端口是 3000，后端是 8080，为什么不会跨域？**
   <details><summary>答案</summary>Vite 的 proxy 配置把 /api 开头的请求转发到 localhost:8080，浏览器以为是同源的（都是 3000），不触发跨域检查。</details>

4. **登录后审计日志页能看到你的登录记录，这是怎么做到的？**
   <details><summary>答案</summary>登录接口的 Controller 方法加了 @Audit 注解，Spring AOP 切面在方法执行时自动记录了操作人/时间/结果到审计表。你完全没写日志代码。</details>

---

📝 **本篇撰写期间修正的代码**：无。
