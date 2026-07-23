# D02 — Embedding 向量化

> **前置要求**：已完成 [D01-RAG 从零理解](D01-rag-from-scratch.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

RAG 的第一步是把文字变成"向量"。但向量是什么？怎么变成的？为什么意思相近的文字向量也相近？

---

## Embedding 是什么？先建立直觉

### 类比：GPS 坐标

地图上每个地点有 GPS 坐标（经度、纬度）。**距离近的地点，坐标也近**——北京和天津的坐标接近，北京和纽约的坐标差很远。

**Embedding 就是文字的"GPS 坐标"**——把一段文字变成一串数字（如 1024 维向量），**意思相近的文字坐标也近**。

```
"机器学习" → [0.12, -0.34, 0.56, ... ] (1024 维)
"深度学习" → [0.11, -0.32, 0.55, ... ] ← 和上面很近（意思相似）
"做菜食谱"  → [-0.45, 0.78, -0.12, ...] ← 和上面很远（意思无关）
```

### 怎么算"相近"

**余弦相似度**（Cosine Similarity）——比较两个向量的方向：

```
相似度 = cos(向量A, 向量B)
值域: -1 到 1
1 = 完全相同方向
0 = 正交（无关）
-1 = 相反方向
```

---

## Embedding 怎么生成的

用专门的 **Embedding 模型**（不是 LLM）：

```java
// 文件：lumina-agent-core/.../rag/OpenAICompatibleEmbeddingModel.java
// 调 Embedding API
public float[] embed(String text) {
    // POST https://api.siliconflow.cn/v1/embeddings
    // { "model": "BAAI/bge-large-zh-v1.5", "input": "文本内容" }
    // 返回 1024 维浮点数组
}
```

### 常用 Embedding 模型

| 模型 | 厂商 | 维度 | 特点 |
|------|------|------|------|
| BAAI/bge-large-zh-v1.5 | 硅基流动 | 1024 | 中文效果好，免费 |
| text-embedding-3-small | OpenAI | 1536 | 通用 |
| text-embedding-v2 | 阿里 DashScope | 1536 | 中文 |

> 💡 Embedding 模型和聊天模型不同——聊天模型生成文字，Embedding 模型生成向量。两者不能混用。

---

## 分块（Chunking）

文档太长不能整篇 Embedding（精度差、超长度）。先切成小块：

```
一篇 10000 字的文档
  ↓ 按 500 字/块 + 50 字重叠切割
块1: 第 1-500 字
块2: 第 451-950 字（和块1重叠50字，保持上下文连贯）
块3: 第 901-1400 字
...
  ↓ 每块独立 Embedding
每块变成一个向量
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| Embedding | 文字 → 数字向量（GPS 坐标） |
| 余弦相似度 | 比较两个向量方向，1=完全相似 |
| 分块 | 文档切成 500 字小块再 Embedding |
| Embedding 模型 | 专门生成向量的模型（不是 LLM） |

> 🚀 [D03 — 向量数据库 →](D03-vector-database.md)

---

📝 **本篇撰写期间修正的代码**：无。
