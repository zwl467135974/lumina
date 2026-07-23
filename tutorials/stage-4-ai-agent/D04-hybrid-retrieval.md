# D04 — 混合检索

> **前置要求**：已完成 [D03-向量数据库](D03-vector-database.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

纯向量检索有一个弱点——**精确关键词匹配不如传统搜索**。用户搜"GLM-4"这个精确型号名时，向量检索可能返回"GLM-3"（意思相似但型号不对）。

**混合检索**同时用向量检索 + 关键词检索，取长补短。

---

## 两路检索

### 向量检索（语义相似）

```
查询: "怎么部署应用"
向量检索找: "应用上线流程"（语义相似，但没出现"部署"二字）
```

### 关键词检索（精确匹配）

```
查询: "GLM-4"
关键词检索找: 包含"GLM-4"的段落（精确匹配）
```

### 混合 = 两路并行 + 融合

```java
// 文件：lumina-agent-core/.../rag/HybridKnowledge.java
// 两路并行检索，RRF 融合
List<SearchResult> vectorResults = vectorSearch(query);      // 向量路
List<SearchResult> keywordResults = keywordSearcher.search(query);  // 关键词路

// RRF 融合两路结果
List<SearchResult> merged = rrfMerge(vectorResults, keywordResults);
```

---

## 关键词检索：MySQL FULLTEXT

Lumina 把文档原文双写到 MySQL 的 `lumina_knowledge_chunk` 表，用 FULLTEXT 索引做关键词搜索：

```sql
-- Flyway V28 创建的表
CREATE TABLE lumina_knowledge_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT,
    FULLTEXT INDEX idx_content (content) WITH PARSER ngram   -- ngram 支持中文
);
```

```sql
-- 关键词查询
SELECT * FROM lumina_knowledge_chunk
WHERE MATCH(content) AGAINST('GLM-4' IN BOOLEAN MODE);
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 纯向量检索 | 语义相似但精确匹配弱 |
| 纯关键词检索 | 精确匹配但不懂同义词 |
| 混合检索 | 两路并行 + 融合（取长补短） |
| 关键词路 | MySQL FULLTEXT + ngram 中文分词 |

> 🚀 [D05 — RRF 融合 + Rerank →](D05-rrf-rerank.md)

---

📝 **本篇撰写期间修正的代码**：无。
