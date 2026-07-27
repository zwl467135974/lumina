-- 预算规则表新增 scope_id_str 列（CONVERSATION 范围用，存储 conversationUuid）
ALTER TABLE `lumina_budget_rule`
    ADD COLUMN `scope_id_str` VARCHAR(100) NULL COMMENT '范围 ID（字符串，CONVERSATION 范围存 conversationUuid）' AFTER `scope_id`;
