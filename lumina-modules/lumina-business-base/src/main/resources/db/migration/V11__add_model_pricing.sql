-- V11: 模型价格配置表

CREATE TABLE IF NOT EXISTS lumina_model_pricing (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider        VARCHAR(50) NOT NULL COMMENT '模型提供商（deepseek/openai/siliconflow 等）',
    model_name      VARCHAR(100) NOT NULL COMMENT '模型名称',
    input_price     DECIMAL(10, 6) NOT NULL COMMENT '输入 token 单价（元/千 token）',
    output_price    DECIMAL(10, 6) NOT NULL COMMENT '输出 token 单价（元/千 token）',
    currency        VARCHAR(10) NOT NULL DEFAULT 'CNY',
    is_active       TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_provider_model (provider, model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型价格配置';

-- 种子数据：常见模型价格（元/千 token，参考 2024-2025 公开定价）
INSERT INTO lumina_model_pricing (provider, model_name, input_price, output_price, currency) VALUES
    ('deepseek', 'deepseek-chat', 0.001000, 0.002000, 'CNY'),
    ('deepseek', 'deepseek-reasoner', 0.004000, 0.016000, 'CNY'),
    ('openai', 'gpt-4o', 0.017500, 0.070000, 'CNY'),
    ('openai', 'gpt-4o-mini', 0.001050, 0.004200, 'CNY'),
    ('siliconflow', 'Qwen/Qwen2.5-7B-Instruct', 0.000700, 0.000700, 'CNY'),
    ('siliconflow', 'Qwen/Qwen3-VL-8B-Instruct', 0.000700, 0.000700, 'CNY'),
    ('default', 'default', 0.002000, 0.006000, 'CNY');
