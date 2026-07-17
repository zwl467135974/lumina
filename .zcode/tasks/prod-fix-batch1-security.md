# 生产修复方案 A：密钥与网关安全（P0 核心）

## 改动文件清单（互不冲突）

### A1. JwtUtil — 默认密钥 fail-fast
文件：`lumina-common/src/main/java/io/lumina/common/util/JwtUtil.java`
- 当前：默认密钥 `"dev-only-secret-please-change-..."` 仅 warn 不阻断
- 改为：`@PostConstruct` 中如果 secretKey == DEFAULT_SECRET_KEY，抛 `IllegalStateException` 阻止启动
- 保留 DEFAULT_SECRET_KEY 常量用于比较，但不再作为 @Value fallback
- @Value 改为 `@Value("${lumina.jwt.secret-key:}")` （空字符串默认值）
- 初始化时检查：空字符串或等于默认值 → 抛异常

### A2. CryptoUtil — AES 默认密钥 fail-fast
文件：`lumina-common/src/main/java/io/lumina/common/util/CryptoUtil.java`
- 当前：`DEFAULT_KEY = "lumina-dev-default-key-do-not-use-in-production"` 硬编码
- 改为：从环境变量 `LUMINA_CRYPTO_KEY` 读取，未设置时用默认值但打印 warn
- encrypt/decrypt 方法中如果 key 是默认值 → log.warn（不阻断，因为阻断会导致已有数据无法解密）
- 新增 `setKey(String key)` 静态方法供 Spring 初始化时注入

### A3. 网关白名单路径剥离身份头
文件：`lumina-gateway/src/main/java/io/lumina/gateway/filter/JwtAuthenticationGatewayFilterFactory.java`
- 当前：白名单路径直接 `chain.filter(exchange)` 放行，不剥离客户端伪造的 `X-Roles`/`X-User-Id` 等头
- 改为：在 filter 入口（白名单检查之前或之后），统一移除所有 `X-User-Id`/`X-Username`/`X-Tenant-Id`/`X-Roles`/`X-Permissions` 头
- 这样：白名单路径放行时没有身份头；非白名单路径通过 JWT 验证后由网关注入身份头
- 实现：
```java
// 在 filter lambda 开头，无论是否白名单，都先剥离客户端可能携带的身份头
ServerHttpRequest request = exchange.getRequest().mutate()
    .headers(h -> {
        h.remove("X-User-Id");
        h.remove("X-Username");
        h.remove("X-Tenant-Id");
        h.remove("X-Roles");
        h.remove("X-Permissions");
    })
    .build();
exchange = exchange.mutate().request(request).build();
```

### A4. Nacos optional 改为非 optional
文件：
- `lumina-modules/lumina-business-base/src/main/resources/application.yml`
- `lumina-modules/lumina-business-agent/src/main/resources/application.yml`
- `lumina-gateway/src/main/resources/application.yml`
- 当前：`import: optional:nacos:lumina-xxx.yaml`
- 改为：`import: nacos:lumina-xxx.yaml`（去掉 optional，Nacos 挂了服务拒绝启动）
- 注意：gateway 的 application.yml 可能格式不同，只改 `optional:nacos` → `nacos`

## 验证
mvn compile -q
