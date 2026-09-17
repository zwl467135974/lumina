-- V54: 工具使用明细（生产数据驱动的工具集蒸馏——open-code-review 模式）
-- 每次工具调用一行（含失败），供按 Agent 聚合分析：调用量/成功率/耗时/未用工具推荐

CREATE TABLE IF NOT EXISTS `lumina_tool_usage` (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_id        BIGINT NULL COMMENT 'Agent ID（trace 上下文缺失时可为空）',
    agent_name      VARCHAR(100) NULL,
    tool_name       VARCHAR(128) NOT NULL,
    success         TINYINT NOT NULL DEFAULT 1 COMMENT '1=成功 0=失败',
    duration_ms     BIGINT NOT NULL DEFAULT 0,
    input_chars     INT NOT NULL DEFAULT 0 COMMENT '入参字符数（上下文成本代理指标）',
    result_chars    INT NOT NULL DEFAULT 0 COMMENT '结果字符数（截断前）',
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_agent_tool (tenant_id, agent_id, tool_name),
    INDEX idx_tenant_agent_time (tenant_id, agent_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 工具调用明细（append-only，仅聚合读取）';
