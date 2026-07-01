-- 知识库文档表新增向量文档ID列（存储切片在向量库中的ID列表，用于删除时清理向量数据）
ALTER TABLE `lumina_knowledge_document`
    ADD COLUMN `vector_doc_ids` TEXT NULL COMMENT '向量库文档ID列表（JSON数组）' AFTER `chunk_count`;
