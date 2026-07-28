# Lumina RAG 知识库设计文档

**版本**: v1.0
**日期**: 2026-07-01
**状态**: 设计确认，待实施

---

## 一、功能概述

为 Agent 提供知识库检索增强生成（RAG）能力：用户上传文档（TXT/Markdown/PDF/Word）→ 自动切片+向量化 → 存向量库 → Agent 对话时自动检索相关知识注入 Prompt，实现基于私有知识库的问答。

---

## 二、技术选型（AgentScope 原生 RAG）

基于 AgentScope 2.0.0 内置 RAG 模块（`io.agentscope.core.rag`），**不引入 Spring AI**。

| 组件 | 选型 | 说明 |
|------|------|------|
| 框架 | AgentScope RAG | `ReActAgent.builder().knowledge().ragMode()` 原生集成 |
| Embedding | `DashScopeTextEmbedding` | text-embedding-v3，1024 维，复用已有 API Key |
| 向量存储 | `InMemoryStore` + `QdrantStore` | 双 Store 配置切换 |
| 文档解析 | `TextReader` / `PDFReader` / `WordReader` | AgentScope 内置，支持自动切片 |
| RAG 模式 | `RAGMode.GENERIC` + `RAGMode.AGENTIC` | 可配置切换 |

### 为什么用 AgentScope 原生而非 Spring AI

- AgentScope 2.0.0 已内置完整 RAG 管线（Reader + Embedding + Store + Knowledge + Agent 集成）
- ReActAgent 原生支持 `.knowledge().ragMode()`，无需手动注入 Prompt
- 复用已有 AgentScope 生态，不引入额外框架
- 工作量减少 ~2 天（切分/向量化/检索/注入全部内置）

---

## 三、架构设计

### 文档入库流程

```
用户上传文档 → KnowledgeController
  → Reader 解析（TextReader/PDFReader/WordReader，按格式自动选择）
  → 切片（SplitStrategy.PARAGRAPH，chunkSize=512，overlap=50）
  → DashScopeTextEmbedding 向量化（1024 维）
  → Store 存储（InMemoryStore 或 QdrantStore，按配置）
  → lumina_knowledge_document 记录元数据（MySQL）
```

### 检索增强流程（Agent 执行时）

```
用户提问 → DefaultAgentExecutionEngine
  → ReActAgent（已配置 knowledge + ragMode + retrieveConfig）
  → GENERIC 模式：自动检索 Top-K 相似片段 → 拼接到用户消息前
  → AGENTIC 模式：Agent 自行决定是否调用 retrieve_knowledge 工具
  → Agent 基于知识 + 历史上下文回答
```

---

## 四、双 Store 策略

| Store | 用途 | 持久化 | 部署 | 容量 |
|-------|------|--------|------|------|
| `InMemoryStore` | 单元测试 / 快速验证 | ❌ 重启丢失 | 零部署（JVM 内存） | <10K 文档 |
| `QdrantStore` | 开发 + 生产 | ✅ 磁盘持久化 | Qdrant 容器（:6334） | 百万级 |

两者**都实现**，本地开发也部署 Qdrant 验证两条路径。配置切换：

```yaml
lumina:
  rag:
    store-type: qdrant    # memory / qdrant
```

---

## 五、RAG 模式

| 模式 | 原理 | 优点 | 缺点 | 适合 |
|------|------|------|------|------|
| `GENERIC` | 每次提问自动检索，结果注入消息 | 简单可靠，必定有知识增强 | 简单问题（如"你好"）也检索 | 知识问答型 Agent |
| `AGENTIC` | Agent 自行判断是否需检索 | 灵活，不浪费检索 | 依赖 LLM 推理能力 | 复杂任务型 Agent |

**默认 GENERIC**，可配置切换为 AGENTIC：

```yaml
lumina:
  rag:
    mode: generic    # generic / agentic
```

---

## 六、文档格式

| Reader | 格式 | Maven 依赖 |
|--------|------|-----------|
| `TextReader` | TXT / Markdown | 无（原生） |
| `PDFReader` | PDF | `org.apache.pdfbox:pdfbox` |
| `WordReader` | .doc / .docx | `org.apache.poi:poi-ooxml` |

切片策略：`SplitStrategy.PARAGRAPH`，chunkSize=512，overlap=50。

ImageReader 暂不支持（需多模态 Embedding 模型，DashScope text-embedding 不支持图片）。

---

## 七、数据模型

### lumina_knowledge_document（Flyway V5）

```sql
CREATE TABLE lumina_knowledge_document (
    document_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文档ID',
    document_uuid VARCHAR(64) NOT NULL COMMENT '文档UUID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    agent_id BIGINT DEFAULT NULL COMMENT '关联Agent ID（NULL=全局知识库）',
    title VARCHAR(200) COMMENT '文档标题',
    format VARCHAR(20) NOT NULL COMMENT '格式（txt/md/pdf/doc/docx）',
    chunk_count INT DEFAULT 0 COMMENT '切片数',
    file_size BIGINT COMMENT '文件大小（字节）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1-正常 0-处理中 -1-失败）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (document_id),
    UNIQUE KEY uk_document_uuid (document_uuid),
    KEY idx_tenant_agent (tenant_id, agent_id)
);
```

向量数据存 Qdrant/InMemoryStore（不含在 MySQL），通过 `document_uuid` 关联。

---

## 八、API 设计

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/knowledge/documents` | 上传文档（multipart/form-data，field=file + agentId） |
| GET | `/api/v1/knowledge/documents` | 文档列表（分页，可选 agentId 过滤） |
| DELETE | `/api/v1/knowledge/documents/{uuid}` | 删除文档（含向量） |
| POST | `/api/v1/knowledge/search` | 检索测试（body: query → 返回 Top-K 片段+分数） |
| PUT | `/api/v1/knowledge/agents/{agentId}` | 绑定知识库到 Agent |

---

## 九、前端设计

新增页面 `/knowledge`（知识库管理）：

- **文档上传区**：拖拽/选择文件（支持 txt/md/pdf/doc/docx），显示上传进度 + 处理状态
- **文档列表表格**：标题 / 格式(tag) / 切片数 / 文件大小 / 状态 / 操作(删除)
- **检索测试面板**：输入框 → 查询 → 显示 Top-K 相似片段 + 相关度分数

路由注册到 `monitorRoutes` 同级的新 `knowledgeRoutes`。

---

## 十、部署：Qdrant

```yaml
# docker-compose.yml
qdrant:
  image: qdrant/qdrant:v1.12
  container_name: lumina-qdrant
  restart: unless-stopped
  ports:
    - "6333:6333"    # REST API / Web UI
    - "6334:6334"    # gRPC（AgentScope 连接）
  volumes:
    - qdrant-data:/qdrant/storage
  networks:
    - lumina-network
```

资源占用：~200MB 内存。Web UI：http://localhost:6333/dashboard。

---

## 十一、配置项汇总

```yaml
lumina:
  rag:
    enabled: true
    store-type: qdrant                    # memory / qdrant
    mode: generic                         # generic / agentic
    embedding:
      model: text-embedding-v3
      dimensions: 1024
    retrieve:
      limit: 3                            # Top-K
      score-threshold: 0.3                # 相似度阈值
    reader:
      chunk-size: 512
      overlap: 50
      split-strategy: PARAGRAPH           # PARAGRAPH/TOKEN/SENTENCE/CHARACTER
    qdrant:
      host: localhost:6334                # gRPC 地址
      collection: lumina_knowledge        # 集合名
```

---

## 十二、工作量

| 阶段 | 内容 | 天数 |
|------|------|------|
| 1 | 依赖（PDFBox+POI）+ 配置类 + Store 工厂（双 Store） | 0.5 |
| 2 | 文档上传管线（Reader→切片→Embedding→Store）+ KnowledgeService | 1 |
| 3 | Agent 集成（DefaultAgentExecutionEngine 加 knowledge）+ API | 0.5 |
| 4 | 前端知识库管理页 | 1 |
| 5 | Qdrant docker-compose + 端到端测试 | 0.5 |
| **合计** | | **3.5** |
