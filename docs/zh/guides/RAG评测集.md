# RAG 评测集（golden set）

> 回答 v3.12 评审缺口 P1-7："RAG 无评测集——检索质量只有单点样本
> （score=0.654），知识飞轮'变厚'但无法证明'变好'"。本文件定义评测数据
> 格式、运行方式与维护纪律。

## 一、为什么需要

分块策略（chunkSize/overlap/splitStrategy）、向量模型、Reranker、混合检索
权重——任何一个改动都可能让检索质量**静默劣化**。golden set 是人工标注的
"查询 → 期望命中文档"对照表，把检索质量变成可回归的量化指标（hit@k / MRR）。

## 二、数据格式（JSONL，一行一例）

```json
{"query": "报销流程需要哪些材料", "limit": 5, "expectedDocUuids": ["a1b2..."], "minHitAtK": 1}
{"query": "差旅标准里住宿上限是多少", "limit": 5, "expectedDocUuids": ["c3d4...", "e5f6..."]}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| query | ✅ | 真实用户问法的采样（不要只写"关键词"，写自然语言） |
| limit |  | 检索条数，默认 5 |
| expectedDocUuids | ✅ | 期望命中的文档 uuid 列表（入库返回的 docUuid）；也支持**内容片段**（结果 content 的子串），适合无 uuid 的旧库 |
| minHitAtK | | 单例最低命中数，默认 1；设 0 表示该例仅统计不阻断 |

示例文件：`scripts/rag/golden-set.example.jsonl`。

## 三、运行（环境门控，默认跳过）

```bash
# 前提：目标知识库已入库（含期望命中的文档）、Qdrant 与向量化密钥可用
export LUMINA_RAG_GOLDEN_SET=/abs/path/golden-set.jsonl
# 可选阈值（默认 hit@k ≥0.8、MRR ≥0.6）
export LUMINA_RAG_MIN_HIT_RATE=0.8
export LUMINA_RAG_MIN_MRR=0.6

mvn test -pl lumina-modules/lumina-business-agent -Dtest=RagGoldenSetEvaluationTest
```

未设置环境变量时测试**自动跳过**（assumeTrue 门控）——评测需要真实向量
环境与人工标注数据，不适合无差别跑在 CI；建议作为**变更拦截**：改分块
策略/换向量模型/调检索权重的分支，本地跑一次再合入。

## 四、维护纪律

1. **规模**：起步 30~50 例即可有统计意义；按"每类知识文档 × 典型问法"
   采样，包含 3~5 个**故意刁钻**的改写问法（同义改写/口语化）。
2. **更新时机**：知识库大版本入库后补标注；废弃文档的用例移除而非保留
   （保留只会制造永久红灯）。
3. **阈值含义**：hit@k 是"能找到"的底线，MRR 是"排得靠前"的质量——
   两者都掉通常意味着 reranker/权重问题；只掉 MRR 多为排序问题。
4. 评测结果与调整记录回填本文件附录，形成"改动 → 指标"对照史。

## 五、与评审清单的关系

P1-7 的"闭环"定义 = 真实环境标注 ≥30 例 + 首轮基线数值回填本文附录。
骨架（`RagGoldenSetEvaluationTest`）与示例文件已就位，等数据。
