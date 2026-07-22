-- ============================================================
-- Lumina 智能运维平台 — Agent 种子数据
--
-- 创建 3 个运维 Agent：
--   1. ops-inspector: 运维巡检 Agent（Plan-Execute + 工具 + KB + 限流/并发）
--   2. ops-reporter:  运维报告 Agent（ReAct + 知识库）
--   3. ops-alerter:    告警通知 Agent（ReAct，用于工作流末端）
--
-- 前置条件：
--   1. 已创建知识库（通过前端上传 kb-nginx-sop.md / kb-database-sop.md / kb-incident-response.md）
--   2. 已创建运维 Prompt（通过前端创建，agent_type 对应 ops-inspector / ops-reporter）
--   3. 将下面的 @KB_ID 替换为实际的知识库 ID
--
-- 注意：tools 字段引用 OpsToolProvider 注册的工具名
--       lumina.ops.enabled=true 时 OpsToolProvider 才生效
-- ============================================================

-- 运维巡检 Agent（主 Agent，Plan-Execute 模式）
INSERT INTO lumina_agent (agent_name, agent_type, description, llm_config, tools, status, rate_limit, max_concurrent, tenant_id)
VALUES (
    '运维巡检助手',
    'ops-inspector',
    '智能运维巡检 Agent，自动读取系统指标和日志，结合知识库给出诊断建议。使用 Plan-Execute 模式：先规划巡检步骤，再逐步执行。',
    '{"modelType":"openai","modelName":"deepseek-chat","temperature":0.3,"maxTokens":2000}',
    'ops.readLogs,ops.readMetrics,ops.executeCommand',
    1,           -- status=1 启用
    10,          -- rateLimit=10 每分钟最多 10 次
    1,           -- maxConcurrent=1 防止并发巡检冲突
    0            -- tenant_id=0
);

-- 运维报告 Agent（用于生成巡检报告摘要）
INSERT INTO lumina_agent (agent_name, agent_type, description, llm_config, tools, status, rate_limit, max_concurrent, tenant_id)
VALUES (
    '运维报告助手',
    'ops-reporter',
    '根据巡检结果生成结构化运维报告，包含系统状态、异常发现、建议措施。',
    '{"modelType":"openai","modelName":"deepseek-chat","temperature":0.5,"maxTokens":1500}',
    NULL,         -- 不需要运维工具，纯文本生成
    1,
    20,           -- rateLimit=20
    3,            -- maxConcurrent=3
    0
);

-- 告警通知 Agent（工作流末端，格式化告警消息）
INSERT INTO lumina_agent (agent_name, agent_type, description, llm_config, tools, status, rate_limit, max_concurrent, tenant_id)
VALUES (
    '告警通知助手',
    'ops-alerter',
    '将运维异常格式化为告警消息，包含严重度、影响范围、建议操作。',
    '{"modelType":"openai","modelName":"deepseek-chat","temperature":0.2,"maxTokens":500}',
    NULL,
    1,
    20,
    5,
    0
);

-- ============================================================
-- 挂载知识库（需替换 @KB_ID 为实际值）
-- 假设已在前端创建了名为"运维知识库"的 KB，ID 为 @KB_ID
-- ============================================================
-- INSERT INTO lumina_agent_knowledge_base (agent_id, kb_id, tenant_id)
-- VALUES (
--     (SELECT agent_id FROM lumina_agent WHERE agent_type = 'ops-inspector'),
--     @KB_ID,
--     0
-- );

-- ============================================================
-- 运维 Prompt 模板（可选，也可在前端创建）
-- ============================================================
-- INSERT INTO lumina_prompt (name, version, content, description, variables, status, is_active, tenant_id)
-- VALUES (
--     'ops-inspector', 1,
--     '你是一名资深运维工程师，负责系统健康巡检。

-- 工作流程：
-- 1. 调用 ops.readMetrics 读取 CPU、内存、磁盘指标
-- 2. 调用 ops.readLogs 读取 Nginx 和应用日志（各 50 行）
-- 3. 分析指标和日志，判断是否存在异常
-- 4. 如果发现异常，查询知识库寻找对应的处理 SOP
-- 5. 输出诊断报告

-- 报告格式：
-- 【系统状态】正常/警告/严重
-- 【指标摘要】CPU xx%, 内存 xx%, 磁盘 xx%
-- 【异常发现】...
-- 【建议措施】...（引用知识库 SOP）
-- 【严重度】P0/P1/P2/P3',
--     '运维巡检 Agent 的 Prompt', 'task', 1, 1, 0
-- );
