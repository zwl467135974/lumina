# 生产就绪审计任务

分析 Lumina 项目的生产就绪状态。重点检查以下方面，给出具体文件路径和行号。

## 检查清单

### 1. 安全问题
- nacos-config/*.yaml 里的 JWT secret 是否有硬编码不安全默认值
- 是否有 Controller 端点缺少鉴权（@RequirePermission 或 JWT 过滤器）
- 密码处理：PasswordUtil 是否正确使用 BCrypt
- CORS 配置是否过于宽松

### 2. 异常处理
- 搜索所有 catch 块，找出"只 log 不 rethrow"的（吞异常）
- 重点检查：MemoryManager、QdrantRestStore、AuditAspect、DocumentIngestConsumer

### 3. 资源泄漏
- 未关闭的 InputStream/OutputStream
- PDDocument 是否都在 try-with-resources 里
- Process 对象是否都 destroy 了
- 临时文件在异常路径下是否清理

### 4. 配置问题
- spring.config.import 用 optional:nacos 是否意味着 Nacos 挂了服务静默启动空配置
- 是否有硬编码的 localhost URL

### 5. 日志安全
- 搜索 log.info/log.debug 中是否打印了 apiKey、password、token 等敏感信息
- 检查 ChatModelFactory、LlmProviderServiceImpl、AgentController 的日志

### 6. 数据库
- Flyway 迁移是否有非幂等操作（如 INSERT 不带 IGNORE）
- MyBatis 查询是否有遗漏 tenant_id 的

## 输出格式
每个问题：严重程度(P0阻塞/P1重要/P2建议) + 文件路径:行号 + 问题 + 修复建议
只给最关键的 8-12 个问题，控制在 500 字内。
