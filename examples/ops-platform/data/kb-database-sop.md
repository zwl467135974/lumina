# 数据库故障排查 SOP

## 适用场景
应用无法连接数据库、查询超时、连接池耗尽等数据库相关问题。

## 连接池耗尽

### 症状
- 日志报错：`HikariPool-1 - Connection is not available, request timed out after 30000ms`
- 所有请求变慢或超时
- `show processlist` 显示大量 Sleep 连接

### 排查步骤
1. 查看当前连接数：`SHOW STATUS LIKE 'Threads_connected';`
2. 查看连接来源：`SELECT host, COUNT(*) FROM information_schema.processlist GROUP BY host;`
3. 检查慢查询：`SHOW VARIABLES LIKE 'slow_query_log%';` 然后 `tail slow.log`
4. 检查是否有事务未提交：`SELECT * FROM information_schema.innodb_trx;`

### 处理方案
- **临时**：增加连接池大小 `maximum-pool-size=50`，重启应用
- **长期**：优化慢查询，添加索引，缩短事务范围
- **紧急**：`kill` 掉长时间 Sleep 的连接

## OOM (OutOfMemoryError)

### 症状
- 日志报错：`java.lang.OutOfMemoryError: Java heap space`
- 进程被操作系统 OOM Killer 终止（dmesg 可见）
- GC 日志显示频繁 Full GC

### 排查步骤
1. 确认内存使用：`free -m` 和 `jmap -heap <pid>`
2. 检查 JVM 参数：`-Xmx` 是否够用
3. 导出内存快照：`jmap -dump:format=b,file=heap.bin <pid>`
4. 分析大对象：使用 MAT 或 VisualVM 分析 heap dump

### 处理方案
- **临时**：重启应用，增大 `-Xmx4g`
- **长期**：修复内存泄漏（常见于 ThreadLocal 未清理、静态集合无限增长）
- **配置**：添加 `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/`

## 慢查询

### 症状
- 接口响应时间突增
- 数据库 CPU 飙升
- 大量查询排队等待

### 处理方案
1. 开启慢查询日志：`SET GLOBAL slow_query_log = 'ON'; SET GLOBAL long_query_time = 1;`
2. 使用 `EXPLAIN` 分析执行计划
3. 添加缺失索引
4. 避免 `SELECT *`，只查必要字段
5. 大表分页用 `WHERE id > last_id LIMIT 20` 替代 `OFFSET`
