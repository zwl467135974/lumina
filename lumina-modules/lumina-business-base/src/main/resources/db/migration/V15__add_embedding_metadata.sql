-- V15: 知识库文档增加语言和 Embedding 模型元数据（E4 多 Embedding 路由）

ALTER TABLE lumina_knowledge_document
    ADD COLUMN language VARCHAR(10) DEFAULT NULL COMMENT '文档语言（zh/en/ja/ko/auto）' AFTER format,
    ADD COLUMN embedding_model VARCHAR(100) DEFAULT NULL COMMENT '实际使用的 Embedding 模型名称' AFTER language;
