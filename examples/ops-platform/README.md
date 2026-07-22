# Lumina 智能运维平台 — 全能力最佳实践

> 用一个「智能运维」场景，把 Lumina 的 **全部 16 项核心能力**编织成一条完整的业务流程。
> clone 代码 → 配 Key → 一键跑通。

## 10 分钟快速上手

### 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 21+ | 编译运行 Lumina |
| Python | 3.10+ | 运行脚本（纯标准库，无需 pip install） |
| MySQL | 8.0+ | 数据存储 |
| Redis | 6.0+ | 缓存/限流/会话 |

### 步骤 1：构建并启动 Lumina

```bash
# 编译 standalone 可执行 jar
mvn -pl lumina-standalone -am -DskipTests package

# 启动（替换为你的 LLM / RAG API Key）
java -jar lumina-standalone/target/lumina-standalone-1.0.0-SNAPSHOT.jar \
  --LLM_API_KEY=<你的LLM密钥> \
  --RAG_ENABLED=true \
  --RAG_EMBEDDING_API_KEY=<你的Embedding密钥> \
  --CODE_INTERPRETER_ENABLED=true \
  --MCP_ENABLED=true \
  --lumina.ops.enabled=true \
  --REFLECTIVE_MEMORY_ENABLED=true
```

> **LLM API Key**：Lumina 默认使用 OpenAI 兼容接口，可在 [DeepSeek](https://platform.deepseek.com) 或 [硅基流动](https://siliconflow.cn) 获取免费/低价 Key。
>
> **RAG Embedding Key**：用于知识库向量化，DeepSeek/硅基流动的 Embedding API 即可。

### 步骤 2：生成模拟运维数据

```bash
# 生成正常模式数据（90% 请求 200，CPU < 50%）
python3 examples/ops-platform/scripts/gen_mock_data.py --mode normal

# 生成严重故障数据（502 大量出现，CPU > 90%，OOM 日志）
python3 examples/ops-platform/scripts/gen_mock_data.py --mode critical
```

### 步骤 3：运行一键演示

```bash
bash examples/ops-platform/scripts/run_demo.sh
```

脚本通过 API 自动完成 14 步：登录 → 创建知识库 → 上传 SOP → 创建 Agent → 执行巡检 →
验证限流 → 创建 Webhook → 创建预算 → 创建触发器 → 多模态上传 → 注册 MCP → 导入评估 →
OpenAI 兼容调用 → 成本查看。

> **注意**：运行前需先将 `OpsToolProvider.java` 复制到源码树并重新编译：
> ```bash
> cp examples/ops-platform/java/OpsToolProvider.java \
>    lumina-agent-core/src/main/java/io/lumina/agent/tool/OpsToolProvider.java
> mvn -pl lumina-standalone -am -DskipTests package
> ```

### 步骤 4：逐项验证能力

有两种方式：

| 方式 | 文档 | 适合场景 |
|------|------|---------|
| **curl 命令** | [step-by-step.md](docs/step-by-step.md) | 自动化、可复制粘贴 |
| **前端 UI** | [frontend-guide.md](docs/frontend-guide.md) | 不想写命令，纯浏览器操作 |

---

## 文件说明

```
examples/ops-platform/
├── README.md                  ← 你在这里
├── java/
│   └── OpsToolProvider.java   运维工具（Agent 可调用：读日志/读指标/执行命令）
├── scripts/
│   ├── gen_mock_data.py       生成模拟运维数据（normal/warning/critical）
│   ├── webhook_receiver.py    Webhook 接收端（验证 HMAC 签名）
│   ├── mcp_fileserver.py      简易 MCP 文件服务器（stdio）
│   ├── gen_chart.py           生成多模态测试图（架构图/CPU趋势图）
│   └── run_demo.sh            一键演示脚本
├── data/
│   ├── kb-nginx-sop.md        运维 SOP：Nginx 故障排查
│   ├── kb-database-sop.md     运维 SOP：数据库故障排查
│   ├── kb-incident-response.md 运维 SOP：故障分级标准
│   └── eval-dataset.yaml      评估数据集（10 个巡检测试用例）
├── config/
│   ├── agents.sql             创建 3 个运维 Agent 的 SQL
│   ├── workflow-dag.yaml      DAG 巡检工作流定义
│   ├── workflow-flowable.bpmn20.xml  Flowable BPMN 流程定义
│   ├── trigger.sql            每小时 Cron 触发器
│   └── ab-test.json           A/B 测试配置
└── docs/
    ├── step-by-step.md        16 步操作指南
    ├── step-by-step.md        16 步 curl 操作指南
    ├── frontend-guide.md      纯前端 UI 操作指南
    └── troubleshooting.md     常见问题
```

---

## 16 项能力速览

| # | 能力 | 对应文件/操作 |
|---|------|-------------|
| 1 | Agent 执行 | 创建 ops-inspector Agent 并执行 |
| 2 | 多模态 | 上传 gen_chart.py 生成的图片 |
| 3 | RAG 检索 | 上传 kb-*.md 到知识库 |
| 4 | 知识库隔离 | Agent 挂载指定 KB |
| 5 | 自定义工具 | OpsToolProvider.java |
| 6 | DAG 工作流 | workflow-dag.yaml |
| 7 | Flowable | workflow-flowable.bpmn20.xml |
| 8 | 人工审批 | 工作流 human 节点 + resume |
| 9 | Cron 触发器 | trigger.sql |
| 10 | 限流/并发 | agents.sql 中的 rateLimit/maxConcurrent |
| 11 | 预算控制 | 通过 API 创建 Budget 规则 |
| 12 | Code Interpreter | Agent 调用 code.execute 生成图表 |
| 13 | Webhook 通知 | webhook_receiver.py |
| 14 | MCP | mcp_fileserver.py |
| 15 | 评估/A/B | eval-dataset.yaml + ab-test.json |
| 16 | OpenAI 兼容 | curl /v1/chat/completions |

---

## 常见问题

见 [troubleshooting.md](docs/troubleshooting.md)。
