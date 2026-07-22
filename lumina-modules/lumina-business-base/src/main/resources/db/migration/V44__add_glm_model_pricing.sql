-- V44: 补充 GLM/智谱/Kimi/Ollama 等常见模型的价格配置
-- 以及 model-pricing 权限种子

-- 智谱 GLM 系列
INSERT INTO lumina_model_pricing (provider, model_name, input_price, output_price, currency, is_active) VALUES
('glm', 'glm-4-flash', 0.000100, 0.000100, 'CNY', 1),
('glm', 'glm-4', 0.050000, 0.050000, 'CNY', 1),
('glm', 'glm-4-air', 0.001000, 0.001000, 'CNY', 1),
('glm', 'glm-4-airx', 0.002000, 0.002000, 'CNY', 1),
('glm', 'glm-4-long', 0.001000, 0.001000, 'CNY', 1),
('glm', 'glm-4-plus', 0.050000, 0.050000, 'CNY', 1),
('glm', 'glm-4v', 0.050000, 0.050000, 'CNY', 1),
('glm', 'glm-3-turbo', 0.001500, 0.001500, 'CNY', 1),

-- Kimi (Moonshot)
('kimi', 'moonshot-v1-8k', 0.012000, 0.012000, 'CNY', 1),
('kimi', 'moonshot-v1-32k', 0.024000, 0.024000, 'CNY', 1),
('kimi', 'moonshot-v1-128k', 0.060000, 0.060000, 'CNY', 1),

-- 通义千问 (DashScope)
('dashscope', 'qwen-turbo', 0.002000, 0.006000, 'CNY', 1),
('dashscope', 'qwen-plus', 0.004000, 0.012000, 'CNY', 1),
('dashscope', 'qwen-max', 0.040000, 0.120000, 'CNY', 1),

-- Anthropic Claude
('anthropic', 'claude-3-opus', 0.105000, 0.525000, 'CNY', 1),
('anthropic', 'claude-3-sonnet', 0.021000, 0.105000, 'CNY', 1),
('anthropic', 'claude-3-haiku', 0.001050, 0.005250, 'CNY', 1),

-- Ollama 本地模型（零成本）
('ollama', 'llama3', 0.000000, 0.000000, 'CNY', 1),
('ollama', 'qwen2.5', 0.000000, 0.000000, 'CNY', 1),
('ollama', 'default', 0.000000, 0.000000, 'CNY', 1)
ON DUPLICATE KEY UPDATE update_time = NOW();

-- 补充 model:pricing 权限种子（挂在 system:model 权限树下）
-- lumina_permission 列名: permission_id / parent_id / permission_code / permission_name / permission_type / sort_order / status
INSERT INTO lumina_permission (parent_id, permission_code, permission_name, permission_type, sort_order, status)
SELECT p.permission_id, 'model:pricing', '模型价格管理', 2, 5, 1
FROM lumina_permission p
WHERE p.permission_code = 'system:model'
  AND NOT EXISTS (SELECT 1 FROM lumina_permission WHERE permission_code = 'model:pricing');

-- SUPER_ADMIN 和 TENANT_ADMIN 自动获得新权限
-- lumina_role 列名: role_id / role_code ; lumina_role_permission 列名: id / role_id / permission_id
INSERT INTO lumina_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM lumina_role r
CROSS JOIN lumina_permission p
WHERE r.role_code IN ('SUPER_ADMIN', 'TENANT_ADMIN')
  AND p.permission_code = 'model:pricing'
  AND r.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM lumina_role_permission rp
    WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id
  );
