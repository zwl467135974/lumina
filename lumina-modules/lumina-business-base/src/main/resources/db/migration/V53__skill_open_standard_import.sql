-- V53: Skill 开放标准互操作（SKILL.md 导入/导出 + 上架安全体检）
-- 对接 Anthropic Agent Skills 开放标准：导入的技能记录来源与体检结论，可疑技能默认禁用待人工复核

ALTER TABLE `lumina_skill`
    ADD COLUMN `source` VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT '来源 MANUAL=手工创建 IMPORT=SKILL.md导入' AFTER `enabled`,
    ADD COLUMN `scan_status` VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '体检状态 NONE=未体检 PASSED=通过 FLAGGED=可疑(禁用待复核)' AFTER `source`,
    ADD COLUMN `scan_report` TEXT NULL COMMENT '体检报告 JSON（findings 列表）' AFTER `scan_status`;
