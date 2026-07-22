-- ============================================================
-- Lumina 智能运维平台 — Cron 定时触发器
--
-- 每小时整点自动触发运维巡检
--
-- 前置条件：
--   - ops-inspector Agent 已创建
--   - 将下面的 agent_id 替换为实际值
--
-- 注意：cron 表达式为 Spring 6 字段格式：秒 分 时 日 月 周
--       0 0 * * * * = 每小时整点
-- ============================================================

INSERT INTO lumina_agent_trigger (name, agent_id, cron_expr, input_text, misfire_policy, enabled, tenant_id)
VALUES (
    '每小时系统巡检',
    1,                       -- ← 替换为 ops-inspector 的 agent_id
    '0 0 * * * *',           -- 每小时整点（Spring 6 字段：秒 分 时 日 月 周）
    '请执行系统健康巡检：1.读取 CPU/内存/磁盘指标 2.读取 Nginx 和应用日志 3.分析是否存在异常 4.如发现异常查询知识库 SOP 5.输出诊断报告',
    'FIRE_ONCE',             -- 错过策略：补触发一次
    1,                       -- 启用
    0                        -- tenant_id=0
);

-- ============================================================
-- 验证触发器状态（创建后可查询 next_fire_at）
-- ============================================================
-- SELECT id, name, agent_id, cron_expr, next_fire_at, enabled
-- FROM lumina_agent_trigger
-- WHERE name = '每小时系统巡检';
