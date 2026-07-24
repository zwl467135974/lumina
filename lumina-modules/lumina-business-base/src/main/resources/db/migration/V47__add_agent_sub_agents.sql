-- Agent 表新增子 Agent 配置列（MultiAgent Supervisor 模式）
ALTER TABLE `lumina_agent`
    ADD COLUMN `sub_agents` TEXT NULL COMMENT '子 Agent 配置 JSON（MultiAgent 模式的专家列表）' AFTER `llm_config`;
