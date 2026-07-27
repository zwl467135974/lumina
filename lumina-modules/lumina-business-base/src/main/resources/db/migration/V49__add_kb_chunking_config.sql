-- 知识库级分块策略配置（NULL = 使用全局默认）
ALTER TABLE `lumina_knowledge_base`
    ADD COLUMN `chunk_size` INT NULL COMMENT '分块大小（Token 数，NULL=全局默认 512）' AFTER `description`,
    ADD COLUMN `overlap` INT NULL COMMENT '分块重叠（Token 数，NULL=全局默认 50）' AFTER `chunk_size`,
    ADD COLUMN `split_strategy` VARCHAR(20) NULL COMMENT '分块策略（PARAGRAPH/CHARACTER/TOKEN/SEMANTIC，NULL=全局默认）' AFTER `overlap`;
