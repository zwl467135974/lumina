# D03 — 向量数据库 Qdrant

> **前置要求**：已完成 [D02-Embedding](D02-embedding-vectors.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

向量生成后要存起来、检索时要快速找到最相似的。传统数据库（MySQL）做不了向量相似度搜索——这需要专门的**向量数据库**。Lumina 用 **Qdrant**。

---

## 向量数据库 vs 传统数据库

| | MySQL | Qdrant |
|---|---|---|
| 存什么 | 结构化数据 | 向量 + 元数据 |
| 查询方式 | WHERE 精确匹配 | 相似度搜索 |
| 查询语句 | SQL | REST API |
| 典型查询 | `WHERE id = 1` | "找和这个向量最相似的 5 个" |

---

## Qdrant 基本操作

### 写入（存向量）

```java
// 文件：lumina-agent-core/.../rag/QdrantRestStore.java 的 doAdd 方法
// POST /collections/{collection}/points
{
  "points": [
    {
      "id": "uuid-1",
      "vector": [0.12, -0.34, 0.56, ...],   // 向量
      "payload": {                            // 元数据
        "content": "请假需提前3天提交...",     //   原文
        "source": "hr-handbook.pdf",          //   来源
        "tenantId": 1,                        //   租户（隔离用）
        "kbId": 5                             //   知识库 ID
      }
    }
  ]
}
```

### 检索（相似度搜索）

```java
// 文件：QdrantRestStore.java 的 doSearch 方法
// POST /collections/{collection}/points/search
{
  "vector": [0.11, -0.33, 0.55, ...],   // 查询向量
  "limit": 5,                             // 返回前 5 个最相似的
  "score_threshold": 0.7,                 // 相似度阈值（低于 0.7 不返回）
  "filter": {                             // 过滤条件（关键！）
    "must": [
      { "key": "tenantId", "match": { "value": 1 } },   // 只查租户1的
      { "key": "kbId", "match": { "any": [3, 5] } }     // 只查知识库3和5的
    ]
  }
}
```

### Qdrant 返回

```json
{
  "result": [
    { "id": "uuid-1", "score": 0.95, "payload": { "content": "请假需提前3天..." } },
    { "id": "uuid-2", "score": 0.88, "payload": { "content": "年假每年10天..." } }
  ]
}
```

---

## 为什么用 REST 而不是 gRPC

```java
// QdrantRestStore.java 注释
// 用 REST API（端口 6333），不依赖 gRPC（端口 6334）
// 原因：gRPC 在某些环境（如 Docker）有 SSL/TLS 兼容问题
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 向量数据库 | 专门做向量相似度搜索的数据库 |
| Qdrant | Lumina 用的向量库（REST API） |
| 写入 | 向量 + payload（原文/来源/租户/kbId） |
| 检索 | 查询向量 + limit + score_threshold + filter |
| filter | 按租户/知识库过滤（隔离的关键） |

> 🚀 [D04 — 混合检索 →](D04-hybrid-retrieval.md)

---

📝 **本篇撰写期间修正的代码**：无。
