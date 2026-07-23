# D08 — 向量层租户隔离

> **前置要求**：已完成 [D07-知识库联邦](D07-knowledge-base-federation.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

MySQL 层有多租户隔离（TenantLineHandler 自动加 tenant_id）。但 **Qdrant 向量库不走 MyBatis**——那层隔离管不到它。怎么保证 A 租户的向量检索不会查到 B 租户的？

这是 Lumina v3.4 修复的**安全漏洞**。

---

## 漏洞是什么

```java
// ❌ 修复前：没有租户过滤
POST /collections/lumina/points/search
{
  "vector": [0.12, ...],
  "limit": 5
  // 没有 filter！所有租户的向量都会被检索
}
```

A 租户检索时可能命中 B 租户的文档——**数据泄露**。

---

## 修复：Qdrant payload filter 下推

```java
// ✅ 修复后：加 tenant_id 过滤
POST /collections/lumina/points/search
{
  "vector": [0.12, ...],
  "limit": 5,
  "filter": {                              // ← 加了过滤！
    "must": [
      { "key": "tenantId", "match": { "value": 1 } }   // 只查租户 1 的
    ]
  }
}
```

### 写入时存 tenantId

```java
// QdrantRestStore.java 的 doAdd
payload.put("tenantId", tenantId);    // 写入时带上租户
```

### 检索时过滤 tenantId

```java
// QdrantRestStore.java 的 doSearch
filter.addMust(condition("tenantId", currentTenantId));    // 检索时只查本租户
```

---

## 为什么要建索引

```java
// Qdrant 里给 tenantId 建索引，加速过滤
PUT /collections/lumina/index
{
  "field_name": "tenantId",
  "field_schema": "keyword"
}
```

不加索引的话，Qdrant 要遍历所有点做过滤——数据量大时慢到不可用。

---

## 和 MySQL 租户隔离的区别

| 层 | 机制 | 自动 |
|----|------|------|
| MySQL | TenantLineHandler 拦截器改写 SQL | ✅ 全自动 |
| Qdrant | 检索时手动加 filter | ❌ 需手动写代码 |

**为什么不同？** Qdrant 不走 MyBatis，拦截器管不到。必须开发者在检索代码里手动加 filter。Lumina 已经在 `QdrantRestStore.doSearch` 里统一处理了。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 漏洞 | 向量检索没租户过滤 → 跨租户数据泄露 |
| 修复 | payload filter 下推 tenant_id |
| 索引 | tenantId 建索引加速过滤 |
| 与 MySQL 区别 | MySQL 全自动，Qdrant 需手动加 filter |

---

## 🎉 模块 D 完成

你已经学完了 RAG 知识库（D01-D08，8 篇），现在你懂得：
- RAG 全流程（检索增强生成）
- Embedding 向量化
- Qdrant 向量数据库
- 混合检索（向量 + 关键词）
- RRF 融合 + Reranker
- OCR 文档解析
- 知识库联邦 + Per-Agent 隔离
- 向量层租户隔离

**RAG 是 Lumina 最核心的 AI 能力之一**，你现在已经深入理解了它。

---

## 下一步

进入 [模块 E：记忆与对话](README.md)（4 篇）——短期记忆、长期记忆、多轮上下文。

> 🚀 [E01 — 短期记忆 →](E01-short-term-memory.md)

---

📝 **本篇撰写期间修正的代码**：无。
