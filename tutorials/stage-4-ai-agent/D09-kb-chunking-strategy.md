# D09 — 知识库级分块策略

> **前置要求**：已完成 [D01 RAG 从零理解](D01-rag-from-scratch.md)、[D07 知识库联邦](D07-knowledge-base-federation.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

RAG 入库的第一步是**把文档切成块**（chunking）。切得不好，检索全废——切太大上下文冗长、切太小语义破碎、切错位置可能切断关键信息。

Lumina 早期只有一个全局配置：`lumina.rag.reader.chunk-size=512` + `splitStrategy=PARAGRAPH`，**所有知识库一刀切**。但现实里：

- **代码文档**：应该按函数/类边界切（AST 切分），段落切分会把一个函数切成两半
- **法律合同**：应该按条款切（"第 X 条"），段落切分会把一款责任描述拆散
- **小说/散文**：段落切分天然合适
- **API 文档**：每个 endpoint 应该独立成块

**一种策略喂所有知识库**是不行的。Lumina v3.4 通过 Flyway V49 迁移，给每个知识库加了独立的分块配置。

---

## 先建立直觉：切蛋糕

把分块想象成切蛋糕——不同的甜点要用不同的切法：

- **海绵蛋糕**（松软、有自然分层）→ **沿着夹层切**（PARAGRAPH 段落策略）：自然分界处下刀，每块干净
- **提拉米苏**（密度高、没分层）→ **每 N 厘米切一刀**（CHARACTER 字符策略）：不管纹理，机械等分
- **马卡龙**（按重量卖）→ **按重量切**（TOKEN 策略）：每块重量一致，不管形状
- **千层蛋糕**（语义分层）→ **按口味层切**（SEMANTIC 语义策略）：识别"这一层是讲什么的"

**关键**：选错切法，蛋糕就毁了。海绵蛋糕硬要按厘米切，会把夹层切断弄得满桌渣。知识库也一样——代码文档用段落切，会把一个函数切成两半。

---

## AgentScope 的四种切法

`io.agentscope.core.rag.reader.SplitStrategy` 枚举提供四种策略：

| 策略 | 切分依据 | 适合 | 不适合 |
|------|---------|------|--------|
| `PARAGRAPH` | 段落/换行等自然边界 | 散文、文章、博客 | 代码、表格密集文档 |
| `CHARACTER` | 每 N 个字符一刀（不管语义） | 纯文本无结构、表格 | 任何有语义边界的文档 |
| `TOKEN` | 按 Token 数切分（重量一致） | 跨语言、需精确控制块大小 | 对语义连续性有要求 |
| `SEMANTIC` | 用 NLP 识别语义边界 | 长报告、学术论文 | 短文本（杀鸡用牛刀） |

构造 Reader 时把策略传进去：

```java
// 文件：lumina-modules/lumina-business-agent/.../mq/DocumentIngestConsumer.java（约 217 行）

private List<Document> parseDocument(Path filePath, String format,
                                     int chunkSize, int overlap,
                                     SplitStrategy splitStrategy) {
    ReaderInput input = ReaderInput.fromPath(filePath);
    switch (format) {
        case "pdf":
            return new PDFReader(chunkSize, splitStrategy, overlap).read(input).block();
        case "doc":
        case "docx":
            return new WordReader(chunkSize, splitStrategy, overlap,
                    false, true, TableFormat.MARKDOWN).read(input).block();
        default:
            return new TextReader(chunkSize, splitStrategy, overlap).read(input).block();
    }
}
```

`chunkSize` 和 `overlap` 控制每块大小和相邻块的重叠（避免边界丢字）。

---

## Lumina 的每知识库配置

### V49 迁移：给 KB 表加三个字段

```sql
-- 文件：lumina-modules/lumina-business-base/.../db/migration/V49__add_kb_chunking_config.sql

ALTER TABLE `lumina_knowledge_base`
    ADD COLUMN `chunk_size` INT NULL
        COMMENT '分块大小（Token 数，NULL=全局默认 512）' AFTER `description`,
    ADD COLUMN `overlap` INT NULL
        COMMENT '分块重叠（Token 数，NULL=全局默认 50）' AFTER `chunk_size`,
    ADD COLUMN `split_strategy` VARCHAR(20) NULL
        COMMENT '分块策略（PARAGRAPH/CHARACTER/TOKEN/SEMANTIC，NULL=全局默认）'
        AFTER `overlap`;
```

**关键设计**：三个字段都允许 `NULL`。`NULL` 的语义是"未自定义，用全局默认"。这样老知识库无需迁移数据就能平滑升级。

### DO 实体映射

```java
// 文件：lumina-modules/lumina-business-agent/.../infrastructure/entity/KnowledgeBaseDO.java

@Data
@TableName("lumina_knowledge_base")
public class KnowledgeBaseDO {
    ...
    /** 分块大小（Token 数，null=全局默认） */
    private Integer chunkSize;
    /** 分块重叠（Token 数，null=全局默认） */
    private Integer overlap;
    /** 分块策略（PARAGRAPH/CHARACTER/TOKEN/SEMANTIC，null=全局默认） */
    private String splitStrategy;
    ...
}
```

`Integer`（而非 `int`）才能承载 NULL 语义。

---

## 三级回退的优先级

这是本节的核心机制。检索一个文档用哪个分块配置？按下面的优先级回退：

```
   ① 知识库级配置（lumina_knowledge_base 表的 chunk_size/overlap/split_strategy）
              ↓ 为 NULL 时
   ② 全局配置（RagProperties.reader，application.yml 里的 lumina.rag.reader.*）
              ↓ 未配置时
   ③ 硬编码默认值（chunkSize=512, overlap=50, splitStrategy=PARAGRAPH）
```

代码里的体现：

```java
// 文件：lumina-modules/lumina-business-agent/.../config/RagProperties.java
@Data
public static class ReaderConfig {
    private int chunkSize = 512;          // ← 第 ② 级，未配置时也兜底到第 ③ 级默认
    private int overlap = 50;
    private String splitStrategy = "PARAGRAPH";
}
```

```java
// 文件：lumina-modules/lumina-business-agent/.../mq/DocumentIngestConsumer.java（约 88-92 行）

// 分块策略：消息中的（KB级）优先，null 回退全局配置
SplitStrategy strategy = msg.getSplitStrategy() != null
        ? SplitStrategy.valueOf(msg.getSplitStrategy())  // ① KB 级
        : getSplitStrategy();                            // ② ③ 全局/默认

private SplitStrategy getSplitStrategy() {
    if (ragProperties != null && ragProperties.getReader() != null) {
        String strategy = ragProperties.getReader().getSplitStrategy();
        if (strategy != null) {
            try {
                return SplitStrategy.valueOf(strategy.toUpperCase());  // ② 全局
            } catch (IllegalArgumentException ignored) {}
        }
    }
    return SplitStrategy.PARAGRAPH;                                    // ③ 硬编码默认
}
```

**为什么三级**？因为不同部署环境的默认偏好不同——云上 SaaS 通常每个 KB 自定义（①），私有部署可能全局统一改 application.yml（②），开发测试直接吃硬编码默认（③）。三级回退让配置灵活性最大化。

---

## 数据流：从 KB 表到 Reader 构造器

完整的链路是 **Controller → Service → MQ 消息 → Consumer → Reader**。**为什么中间要走 MQ？** 因为分块 + Embedding + 入库是耗时操作（大文档几十秒），不能阻塞上传请求。MQ 把"上传"和"处理"解耦。

```
① 上传文档（携带 kbId）
       ↓
② KnowledgeServiceImpl.uploadDocument
   读取 KB 表的 chunk_size/overlap/split_strategy（NULL 回退全局）
       ↓
③ 封装进 DocumentIngestMessage 发送到 RocketMQ
       ↓ （异步）
④ DocumentIngestConsumer.onMessage
   从消息取 chunkSize/overlap/splitStrategy（NULL 回退全局）
       ↓
⑤ parseDocument 用这三个值构造 PDFReader/WordReader/TextReader
```

### 第 ② 步：KnowledgeServiceImpl 读 KB 配置

```java
// 文件：lumina-modules/lumina-business-agent/.../service/impl/KnowledgeServiceImpl.java（约 88-103 行）

// 从知识库配置读取分块策略（KB 配置优先，null 回退全局默认）
int effectiveChunkSize = chunkSize;          // 全局默认初始化（@Value 注入）
int effectiveOverlap = overlap;
String effectiveSplitStrategy = null;        // null 让 consumer 用全局默认
if (knowledgeBaseMapper != null && kbId != null) {
    KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(kbId);
    if (kb != null) {
        if (kb.getChunkSize() != null) effectiveChunkSize = kb.getChunkSize();
        if (kb.getOverlap() != null) effectiveOverlap = kb.getOverlap();
        effectiveSplitStrategy = kb.getSplitStrategy();
        log.info("使用知识库分块配置: kbId={}, chunkSize={}, overlap={}, strategy={}",
                kbId, effectiveChunkSize, effectiveOverlap, effectiveSplitStrategy);
    }
}
```

### 第 ③ 步：封装进 MQ 消息

```java
// 文件：lumina-modules/lumina-business-agent/.../mq/DocumentIngestMessage.java

public class DocumentIngestMessage implements Serializable {
    private static final long serialVersionUID = 2L;     // ← 加字段后版本号升级
    ...
    private int chunkSize;
    private int overlap;
    /** 分块策略（PARAGRAPH/CHARACTER/TOKEN/SEMANTIC，null=全局默认） */
    private String splitStrategy;
}
```

> **注意 `serialVersionUID = 2L`**：给消息类加字段必须升级版本号，否则老 consumer 反序列化新消息会失败（虽然 Java 默认容忍新增字段，但显式升版本是良好实践）。

### 第 ④ 步：Consumer 用配置构造 Reader

```java
// 文件：lumina-modules/lumina-business-agent/.../mq/DocumentIngestConsumer.java（约 88-92 行）

SplitStrategy strategy = msg.getSplitStrategy() != null
        ? SplitStrategy.valueOf(msg.getSplitStrategy()) : getSplitStrategy();

List<Document> docs = parseDocument(filePath, msg.getFormat(),
        msg.getChunkSize(), msg.getOverlap(), strategy);
```

---

## 实战：怎么为不同知识库选策略

| 知识库类型 | 推荐 chunkSize | 推荐 overlap | 推荐 strategy | 理由 |
|-----------|---------------|-------------|--------------|------|
| 代码/API 文档 | 256 | 30 | `PARAGRAPH` | 函数/endpoint 通常按空行分段 |
| 法律合同 | 512 | 100 | `PARAGRAPH` | 条款按段落分，overlap 大防止责任描述被切断 |
| 小说/散文 | 512 | 50 | `PARAGRAPH` | 默认值就够用 |
| 表格密集数据 | 384 | 0 | `CHARACTER` | 表格没有段落边界，按字符机械切更稳定 |
| 中英混排技术文档 | 512 | 50 | `TOKEN` | Token 切分对中英混排更精确 |
| 长篇报告 | 768 | 100 | `SEMANTIC` | 识别章节语义边界，大块保上下文 |

**经验法则**：
- 不确定就用 `PARAGRAPH` + 512 + 50（全局默认就是它，覆盖 80% 场景）
- 检索效果差时先调 `chunkSize`（太小语义破碎、太大上下文冗长）
- 边界丢字严重就加大 `overlap`（代价是存储和检索成本上升）

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 切蛋糕类比 | 不同文档要用不同切法，一刀切会切断语义 |
| 四种策略 | PARAGRAPH（段落）/ CHARACTER（字符）/ TOKEN（按重量）/ SEMANTIC（语义边界） |
| V49 迁移 | 给 lumina_knowledge_base 加 chunk_size/overlap/split_strategy（均 NULL=用全局） |
| 三级优先级 | KB 配置 → RagProperties 全局 → 硬编码默认（512/50/PARAGRAPH） |
| 数据流 | Service 读 KB 配置 → 封进 DocumentIngestMessage → Consumer 构造 Reader |
| MQ 解耦 | 分块/Embedding 耗时，走异步队列不阻塞上传 |

### 自测题

1. 为什么 Lumina 给 lumina_knowledge_base 加的分块字段都允许 NULL？NULL 在这里的语义是什么？
   <details><summary>答案</summary>NULL 表示<b>"未自定义，用全局默认"</b>。这样老知识库无需迁移数据就能平滑升级——已存在的 KB 行这三个字段都是 NULL，自动回退到 RagProperties 全局配置。如果是 NOT NULL 字段，迁移时必须给每个老 KB 填默认值，且无法区分"用户特意选了 PARAGRAPH"和"用户没配用默认"。</details>

2. 检索一个文档的分块配置时，三级回退的优先级是什么？硬编码默认值是多少？
   <details><summary>答案</summary>优先级：<b>① KB 表配置（chunk_size/overlap/split_strategy）</b> → <b>② 全局 RagProperties.reader（application.yml 的 lumina.rag.reader.*）</b> → <b>③ 硬编码默认值</b>。硬编码默认是 chunkSize=512、overlap=50、splitStrategy=PARAGRAPH。</details>

3. DocumentIngestMessage 的 `serialVersionUID = 2L` 为什么不是 1L？加了字段后不改版本号会怎样？
   <details><summary>答案</summary>因为本次给消息类新增了 chunkSize/overlap/splitStrategy 字段，序列化结构变了，所以把 serialVersionUID 从 1L 升到 2L。不改版本号的话，老 consumer 进程反序列化新格式消息时可能抛 InvalidClassException（虽然 Java 默认对新增字段容忍，但显式升版本是规范做法，避免灰度期间老 consumer 解析失败）。</details>

4. 为什么分块配置要先在 KnowledgeServiceImpl 里读，再通过 MQ 消息传给 DocumentIngestConsumer？直接在 consumer 里查 KB 表不行吗？
   <details><summary>答案</summary>理论上直接在 consumer 里查也行，但通过 MQ 消息传递有两个好处：<b>(1) 解耦</b>——consumer 不依赖 KnowledgeBaseMapper，可以独立部署/测试；<b>(2) 时序一致性</b>——上传时 KB 的配置被"快照"进消息，即使上传后用户改了 KB 配置，这份文档仍按上传时的配置处理，避免 race condition。这是事件驱动架构的常见模式。</details>

---

> 🚀 [E01 — 短期记忆 →](E01-short-term-memory.md)

---

📝 **本篇撰写期间修正的代码**：无（KB 级分块策略为 v3.4 已有能力，本节仅做解读）。
