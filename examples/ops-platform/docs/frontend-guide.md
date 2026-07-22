# 前端操作指南 — 纯浏览器 UI 完成全部 16 项能力验证

> 如果你不想写一行 curl 命令，按这个指南在浏览器里也能完成全部操作。
> 前置条件：Lumina standalone 已启动（端口 8080），前端已启动（端口 5173 或通过 standalone 内置访问）。

---

## 准备

1. 打开浏览器访问 `http://localhost:8080`（或前端开发端口）
2. 登录：用户名 `admin`，密码 `admin123`

---

## ④ 知识库管理 + 隔离

### 创建知识库
1. 左侧菜单 → **知识库管理**
2. 点击「新建知识库」
3. 填写：名称 `运维知识库`，描述 `Nginx/数据库/故障分级 SOP`
4. 点击保存

### 上传 SOP 文档
1. 进入刚创建的知识库详情页
2. 点击「上传文档」
3. 依次上传 3 个文件：
   - `examples/ops-platform/data/kb-nginx-sop.md`
   - `examples/ops-platform/data/kb-database-sop.md`
   - `examples/ops-platform/data/kb-incident-response.md`
4. 等待文档状态变为「已分片」（绿色）

---

## ① Agent 执行 + ⑤ 工具 + ⑩ 限流/并发

### 创建运维 Agent
1. 左侧菜单 → **Agent 管理**
2. 点击「创建 Agent」
3. 填写：
   - 名称：`运维巡检助手`
   - 类型：`ops-inspector`
   - 工具：勾选 `ops.readLogs`、`ops.readMetrics`、`ops.executeCommand`
   - 知识库：勾选「运维知识库」
   - 限流：`10`（每分钟最多 10 次）
   - 并发：`1`（最大并发 1）
4. 点击保存

### 执行 Agent
1. Agent 列表中点击「执行」按钮（▶）
2. 输入任务：`执行系统巡检：读取CPU/内存指标，读取Nginx和应用日志`
3. 观察右侧 Chat 面板，Agent 会：
   - 调用 `ops.readMetrics` 读取指标
   - 调用 `ops.readLogs` 读取日志
   - 引用知识库 SOP 给建议
   - 输出诊断报告

---

## ② 多模态执行

1. 先运行 `python3 examples/ops-platform/scripts/gen_chart.py` 生成图片
2. 在 Agent Chat 面板中，点击「上传文件」（📎 图标）
3. 选择 `/tmp/lumina-ops/images/architecture.png`
4. 输入任务：`分析这张系统架构图，描述各组件`
5. Agent 会通过多模态接口识别图片内容

---

## ③ RAG 检索验证

1. 运行 `python3 examples/ops-platform/scripts/gen_mock_data.py --mode critical`（生成异常数据）
2. 在 Agent Chat 中输入：`系统出现大量 502 错误，请查询知识库给出排查步骤`
3. Agent 响应中应包含 Nginx SOP 的排查内容（如 upstream、proxy_read_timeout）

---

## ⑥ DAG 工作流

### 创建工作流
1. 左侧菜单 → **工作流管理**
2. 点击「创建工作流」
3. 名称：`运维巡检流水线`
4. 将 `examples/ops-platform/config/workflow-dag.yaml` 的内容粘贴到 YAML 编辑区
5. **重要**：将 YAML 中的 `agentId: 1`/`2`/`3` 替换为实际 Agent ID
6. 点击保存 → 发布

### 执行工作流
1. 在工作流详情页点击「执行」
2. 输入参数：`inspection_mode = routine`
3. 观察节点执行进度（可视化 DAG 图）

---

## ⑧ 人工审批

1. 工作流执行到 `human-approval` 节点时会**暂停**（状态变为 PAUSED）
2. 在工作流实例详情页看到「等待人工审批」
3. 点击「审批」按钮
4. 选择 `approve`（通过）或 `reject`（拒绝）
5. 工作流继续执行后续节点

---

## ⑬ Webhook 通知

### 启动接收端
1. 终端运行：`python3 examples/ops-platform/scripts/webhook_receiver.py --port 9999`

### 配置 Webhook
1. 左侧菜单 → **通知管理** → **Webhook**
2. 点击「新建 Webhook」
3. 填写：
   - 名称：`运维告警通知`
   - URL：`http://<你的IP>:9999/webhook`
   - 渠道：`WEBHOOK`
   - 订阅事件：勾选 `TASK`、`TRIGGER`、`BUDGET`
4. **保存密钥**（创建后只显示一次）
5. 用保存的密钥重新启动接收端：`python3 webhook_receiver.py --port 9999 --secret <密钥>`
6. 点击「测试」按钮 → 接收端终端应打印收到的通知

---

## ⑨ Cron 定时触发器

1. 左侧菜单 → **触发器管理**（如无此菜单，在 Agent 详情页操作）
2. 点击「创建触发器」
3. 填写：
   - 名称：`每小时巡检`
   - 目标 Agent：选择「运维巡检助手」
   - Cron 表达式：`0 0 * * * *`
   - 任务输入：`执行系统巡检`
   - 错过策略：`FIRE_ONCE`
4. 保存后查看 `next_fire_at`（下次触发时间）

---

## ⑪ 预算控制

1. 左侧菜单 → **预算管理**
2. 点击「创建预算规则」
3. 填写：
   - 规则名称：`运维巡检日预算`
   - 作用域：`AGENT`，选择运维 Agent
   - 周期：`DAILY`
   - 限额：`5.00` 元
   - 告警阈值：`80%`
4. 保存 → 多次执行 Agent 后，在预算页面查看用量

---

## ⑫ Code Interpreter

1. 编辑运维 Agent → 工具列表加上 `code.execute`
2. 在 Chat 中输入：`用 Python 生成一个 CPU 使用率折线图`
3. Agent 会调用 Code Interpreter 执行 Python 代码

---

## ⑭ MCP 接入

> MCP 目前需要通过 API 注册（前端暂无 MCP 管理页面）。使用一条 curl 命令：
```bash
curl -X POST http://localhost:8080/api/v1/mcp/servers \
  -H 'Authorization: Bearer <TOKEN>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"ops-fileserver","transport":"stdio","command":"python3","args":["examples/ops-platform/scripts/mcp_fileserver.py","--root","/tmp/lumina-ops/config"]}'
```

注册后，Agent 可自动调用 `mcp__ops-fileserver__list_files` 和 `read_file` 工具。

---

## ⑮ 评估框架 + A/B 测试

### 评估
1. 左侧菜单 → **Agent 评估**
2. 点击「导入数据集」→ 上传 `examples/ops-platform/data/eval-dataset.yaml`
3. 选择数据集 → 点击「运行评估」
4. 评分方式选择 `LLM_JUDGE`
5. 查看评估报告（通过率、得分分布）

### A/B 测试
1. 先在 **Prompt 管理** 创建两个 Prompt：
   - `ops-inspector-concise`（简洁版）
   - `ops-inspector-detailed`（详细版）
   - 都发布（点击「发布」按钮）
2. 左侧菜单 → **A/B 测试**
3. 点击「创建实验」
4. 选择运维 Agent，创建 2 个变体：
   - 变体 A：权重 50%，Prompt 选 `ops-inspector-concise`
   - 变体 B：权重 50%，Prompt 选 `ops-inspector-detailed`
5. 启动实验
6. 多次执行 Agent 后，查看对比报告

---

## ⑯ OpenAI 兼容端点

> OpenAI 兼容端点需要在 API Token 管理页创建 Token：
1. 左侧菜单 → **系统管理** → **API Token**
2. 点击「创建 Token」→ 名称 `ops-demo`
3. **保存返回的 Token**（sk-xxx，只显示一次）
4. 然后可以用任何 OpenAI SDK 调用：
```python
from openai import OpenAI
client = OpenAI(base_url="http://localhost:8080/v1", api_key="sk-xxx")
response = client.chat.completions.create(
    model="agent-1",
    messages=[{"role": "user", "content": "检查系统状态"}]
)
```

---

## ⑦ Flowable 工作流

1. 左侧菜单 → **工作流管理**
2. 点击「创建工作流」
3. 名称：`运维巡检 Flowable 版`
4. 将 `examples/ops-platform/config/workflow-flowable.bpmn20.xml` 内容粘贴
5. 替换 agentId 为实际值
6. 保存 → 发布 → 执行

---

## 贯穿能力

| 能力 | 前端路径 |
|------|---------|
| 审计日志 | 系统管理 → 审计日志 |
| 成本分析 | 左侧菜单 → 成本分析 |
| 会话历史 | 左侧菜单 → 会话管理 |
| 长期记忆 | Agent 详情 → 长期记忆 |
| Prompt 版本管理 | 左侧菜单 → Prompt 管理 |
| 暗色/亮色切换 | 右上角图标 |
| 中英文切换 | 右上角图标 |
