-- V43: lumina_agent 增加每分钟限流 & 最大并发配置（v3.6 Per-Agent 限流）

ALTER TABLE lumina_agent
    ADD COLUMN rate_limit INT DEFAULT 0 COMMENT '每分钟最大请求数，0=用全局默认';
ALTER TABLE lumina_agent
    ADD COLUMN max_concurrent INT DEFAULT 0 COMMENT '最大并发执行数，0=不限制';
