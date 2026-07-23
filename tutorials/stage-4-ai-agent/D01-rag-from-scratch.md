# D01 — RAG 从零理解

> **前置要求**：已完成 [模块 A-C](README.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

LLM 不知道你公司的私有文档（产品手册、SOP、内部知识库）。怎么让它"知道"？

**RAG（Retrieval-Augmented Generation，检索增强生成）**——先从知识库里"检索"相关内容，再让 LLM "增强"回答。这是模块 D 的开篇，用"开卷考试"类比讲清全流程。

---

## RAG 是什么？先建立直觉

### 类比：开卷考试

闭卷考试（纯 LLM）：你只能靠脑子里的记忆答题——知识有限、可能记错。

开卷考试（RAG）：你可以先翻书找到相关页，再答题——答得更准、更全。

**RAG 就是让 AI 开卷考试**：
1. 用户提问
2. 先从知识库里**检索**最相关的段落
3. 把检索到的段落**塞进 Prompt**（"参考资料: ..."）
4. LLM 基于参考资料生成回答

```
用户: "公司的请假流程是什么？"
  ↓ 检索
知识库找到: "请假需提前 3 天在 OA 系统提交，直属领导审批..."
  ↓ 增强
Prompt 变成:
  "参考资料: 请假需提前 3 天在 OA 系统提交，直属领导审批...
   用户问题: 公司的请假流程是什么？
   请基于参考资料回答。"
  ↓ LLM 生成
回答: "根据公司规定，请假需要提前 3 天在 OA 系统提交申请，由直属领导审批。"
```

---

## 为什么不直接把所有文档塞给 LLM？

1. **上下文窗口有限**——4K-128K Token，文档太多塞不下
2. **太贵**——Token 越多越贵
3. **太乱**——文档全塞进去，LLM 找不到重点反而回答更差

**RAG 的价值**：只检索**最相关**的片段，精准、省钱、效果好。

---

## RAG 的三件套

```java
// 文件：lumina-agent-core/.../rag/RagKnowledgeFactory.java:204-233
@Bean
public Knowledge knowledge(EmbeddingModel embeddingModel, VDBStoreBase store, ...) {
    // 三件套：EmbeddingModel + Store + Knowledge
}
```

| 组件 | 职责 | 类比 |
|------|------|------|
| **EmbeddingModel** | 把文字变成向量 | 给书页贴"GPS 坐标" |
| **VDBStoreBase**（向量库） | 存向量、按相似度检索 | 按 GPS 坐标找最近的书页 |
| **Knowledge** | 封装检索+增强逻辑 | 开卷考试的"翻书+答题"流程 |

---

## RAG 完整流程

### 写入阶段（文档入库）

```
上传文档（PDF/Word/TXT）
  ↓
分块（Chunking）：切成小段落（每段 500 字左右）
  ↓
向量化（Embedding）：每段文字 → 一串数字（向量）
  ↓
存入向量库：每段文字 + 向量 + 元数据（来源/租户）
```

### 查询阶段（用户提问）

```
用户提问
  ↓
向量化：问题 → 向量
  ↓
检索：在向量库里找最相似的段落（余弦相似度）
  ↓
增强：把找到的段落塞进 Prompt
  ↓
LLM 基于参考资料生成回答
```

---

## 在 Lumina 里怎么开启 RAG

```yaml
# application.yml
lumina:
  rag:
    enabled: true                    # 开启 RAG
    store-type: qdrant               # 向量库类型
    embedding:
      provider: openai               # Embedding 模型
      api-key: ${RAG_EMBEDDING_API_KEY}
      base-url: https://api.siliconflow.cn/v1
      model: BAAI/bge-large-zh-v1.5  # 中文 Embedding 模型
      dimensions: 1024
```

### Agent 配置 RAG

```java
// AgentConfig
private List<Long> knowledgeBaseIds;    // Agent 挂载的知识库 ID
```

Agent 执行时，只从挂载的知识库检索——**Per-Agent 隔离**。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| RAG | AI 的开卷考试 |
| 为什么不全塞 | 窗口有限/贵/乱 |
| 三件套 | EmbeddingModel + 向量库 + Knowledge |
| 写入 | 文档→分块→向量化→存库 |
| 查询 | 问题→向量化→检索→增强→生成 |

> 📖 后续 7 篇深入每个环节：Embedding、向量库、混合检索、Rerank、OCR...

> 🚀 [D02 — Embedding 向量化 →](D02-embedding-vectors.md)

---

## 自测题

1. **RAG 和直接问 LLM 有什么区别？**
   <details><summary>答案</summary>RAG 先从知识库检索相关内容，塞进 Prompt 让 LLM 基于参考资料回答。直接问 LLM 只靠训练数据，不知道私有/实时信息。</details>

2. **为什么不把所有文档一次性塞给 LLM？**
   <details><summary>答案</summary>① 上下文窗口有限 ② Token 太贵 ③ 文档太多 LLM 找不到重点。</details>

---

📝 **本篇撰写期间修正的代码**：无。
