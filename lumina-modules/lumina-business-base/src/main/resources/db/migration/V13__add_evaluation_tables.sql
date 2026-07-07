-- V13: 评估框架表

CREATE TABLE IF NOT EXISTS lumina_evaluation_dataset (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(200) NOT NULL COMMENT '数据集名称',
    description     VARCHAR(500) COMMENT '描述',
    agent_type      VARCHAR(50) COMMENT '关联 Agent 类型',
    cases_yaml      TEXT NOT NULL COMMENT '测试用例 YAML',
    case_count      INT NOT NULL DEFAULT 0 COMMENT '用例数量',
    tenant_id       BIGINT NOT NULL,
    create_by       BIGINT,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT NOT NULL DEFAULT 0,
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估数据集';

CREATE TABLE IF NOT EXISTS lumina_evaluation_run (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id      BIGINT NOT NULL COMMENT '数据集 ID',
    dataset_name    VARCHAR(200) NOT NULL COMMENT '数据集名称',
    agent_id        BIGINT COMMENT '被评估 Agent ID',
    agent_type      VARCHAR(50) COMMENT 'Agent 类型',
    scoring_method  VARCHAR(30) NOT NULL COMMENT '评分方法',
    threshold_value DECIMAL(4,2) NOT NULL DEFAULT 0.70 COMMENT '通过阈值',
    total_cases     INT NOT NULL COMMENT '总用例数',
    passed_cases    INT NOT NULL COMMENT '通过用例数',
    pass_rate       DECIMAL(5,2) NOT NULL COMMENT '通过率',
    avg_score       DECIMAL(5,4) NOT NULL COMMENT '平均得分',
    avg_latency_ms  BIGINT COMMENT '平均延迟',
    total_tokens    INT NOT NULL DEFAULT 0 COMMENT '总 Token',
    results_json    TEXT COMMENT '全部用例结果 JSON',
    tenant_id       BIGINT NOT NULL,
    create_by       BIGINT,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_dataset (dataset_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估运行记录';
