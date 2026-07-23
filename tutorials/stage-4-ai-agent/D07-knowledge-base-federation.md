# D07 — 知识库联邦与 Per-Agent 隔离

> **前置要求**：已完成 [D06-OCR](D06-ocr-document-parsing.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

一个企业有多个知识库（HR 手册、技术文档、产品手册……）。不是每个 Agent 都该看到所有知识库——客服 Agent 只看产品手册，IT Agent 只看技术文档。怎么做到 **Per-Agent 隔离**？

---

## 知识库联邦

### 概念

把多个文档归类到不同的**知识库（Knowledge Base）**，每个知识库有独立的管理（上传/删除/检索）。

```
知识库 1: HR 手册（lumina_knowledge_base.id=1）
  ├── 请假制度.pdf
  └── 考勤规则.docx

知识库 2: 技术文档（lumina_knowledge_base.id=2）
  ├── API 手册.pdf
  └── 部署指南.md

知识库 3: 产品手册（lumina_knowledge_base.id=3）
  └── 产品规格.pdf
```

### 中间关联表

```sql
-- lumina_agent_knowledge_base（Agent ↔ 知识库 多对多）
| agent_id | kb_id |
|----------|-------|
| 1        | 1     |  Agent 1 挂载 HR 手册
| 1        | 2     |  Agent 1 也挂载技术文档
| 2        | 3     |  Agent 2 只挂载产品手册
```

---

## Per-Agent 隔离检索

Agent 执行 RAG 时，只从**自己挂载**的知识库检索：

```java
// 文件：DefaultAgentExecutionEngine.java 的 scopedKnowledge 方法
// 装饰器模式：过滤检索结果，只保留 Agent 挂载的 kbId
private Knowledge scopedKnowledge(List<Long> knowledgeBaseIds) {
    return new Knowledge() {
        @Override
        public List<SearchResult> search(String query) {
            // 检索后过滤：只保留 kbId 在 knowledgeBaseIds 里的结果
            return delegate.search(query).stream()
                .filter(r -> knowledgeBaseIds.contains(r.getKbId()))
                .toList();
        }
    };
}
```

**效果**：Agent 2（挂载产品手册）检索时，绝不会看到 HR 手册的内容——即使它们在同一个向量库里。

---

## 知识库管理 API

```java
// 文件：KnowledgeBaseController.java
@PostMapping                         // 创建知识库
@PostMapping("/{kbId}/agents/{agentId}/mount")    // 挂载到 Agent
@DeleteMapping("/{kbId}/agents/{agentId}/mount")  // 卸载
@GetMapping("/agents/{agentId}")                 // 查 Agent 挂了哪些
```

前端在 Agent 编辑页可以多选知识库——勾选即挂载，取消即卸载。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 知识库联邦 | 多知识库分类管理 |
| 关联表 | agent ↔ kb 多对多 |
| Per-Agent 隔离 | Agent 只检索自己挂载的 KB |
| 装饰器模式 | scopedKnowledge 过滤检索结果 |

> 🚀 [D08 — 向量层租户隔离 →](D08-rag-tenant-isolation.md)

---

📝 **本篇撰写期间修正的代码**：无。
