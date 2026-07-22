# Nginx 502/503 故障排查 SOP

## 适用场景
Nginx 作为反向代理时返回 502 Bad Gateway 或 503 Service Unavailable，表示后端服务不可用。

## 502 Bad Gateway

### 常见原因
1. **后端服务未启动** — 应用进程崩溃或正在重启
2. **后端服务端口不对** — Nginx upstream 配置的端口与实际不符
3. **后端响应超时** — 应用处理过慢，超过 proxy_read_timeout
4. **防火墙拦截** — Nginx 与后端之间的网络不通

### 排查步骤
1. 检查后端服务状态：`ps aux | grep java`（确认应用进程存在）
2. 检查端口监听：`netstat -tlnp | grep 8080`（确认 8080 端口在监听）
3. 测试后端连通性：`curl -I http://127.0.0.1:8080/actuator/health`
4. 检查 Nginx 错误日志：`tail -50 /var/log/nginx/error.log`
5. 检查应用日志：查看是否有 OOM、异常重启记录

### 处理方案
- **服务未启动**：重启应用 `systemctl restart lumina-agent`
- **资源不足导致 OOM**：增加 JVM 内存 `-Xmx4g`，或扩容
- **连接池耗尽**：增加 `spring.datasource.hikari.maximum-pool-size`
- **超时配置不当**：调整 `proxy_read_timeout 60s;`

## 503 Service Unavailable

### 常见原因
1. **后端全部节点下线** — upstream 中所有 server 都标记为 down
2. **限流触发** — Nginx limit_req 或 limit_conn 限制
3. **维护模式** — 人工标记服务不可用

### 处理方案
- 检查 upstream 健康状态
- 临时关闭限流：`limit_req zone=api burst=100 nodelay;`
- 检查是否有维护脚本标记了节点下线

## 预防措施
- 配置 Nginx 健康检查：`proxy_next_upstream error timeout http_502 http_503;`
- 设置合理的 `max_fails` 和 `fail_timeout`
- 部署 APM 监控，对 5xx 错误率设置告警阈值（>5%）
- 后端服务配置优雅停机，避免重启瞬间的 502
