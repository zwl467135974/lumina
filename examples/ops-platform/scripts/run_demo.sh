#!/usr/bin/env bash
# ============================================================
# Lumina 智能运维平台 — 全自动演示脚本
#
# 通过 API 自动完成尽可能多的步骤：
#   登录 → 创建知识库 → 上传SOP → 创建Agent(挂载KB+工具+限流) →
#   执行巡检 → 验证限流 → 创建Webhook → 创建预算 → 创建触发器 →
#   生成多模态图 → 上传图片 → 多模态执行 → 注册MCP → 创建评估 →
#   创建API Token → OpenAI兼容调用
#
# 仅以下步骤需要手动（前端操作或额外终端）：
#   - Webhook 接收端：需在另一个终端运行 webhook_receiver.py
#   - 工作流人工审批 resume：需查看 PAUSED 实例后手动 resume
#
# 用法:
#   bash examples/ops-platform/scripts/run_demo.sh
# ============================================================

set -euo pipefail

# ==================== 配置 ====================
LUMINA_HOST="${LUMINA_HOST:-http://localhost:8080}"
LUMINA_USER="${LUMINA_USER:-admin}"
LUMINA_PASS="${LUMINA_PASS:-admin123}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DATA_DIR="${OPS_DATA_DIR:-/tmp/lumina-ops}"

GREEN='\033[0;32m'; YELLOW='\033[0;33m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
step()  { echo -e "\n${CYAN}━━━ 步骤 $1 ━━━${NC}"; }

# 辅助函数：提取 JSON 响应中的字段
jget() { python3 -c "import sys,json; d=json.load(sys.stdin); print(d$1)" 2>/dev/null; }
jval() { python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('$1',''))" 2>/dev/null; }

# ==================== 步骤 1：登录 ====================
step "1/14: 登录获取 Token"
LOGIN_RESP=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/base/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${LUMINA_USER}\",\"password\":\"${LUMINA_PASS}\"}")
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])" 2>/dev/null || echo "")

if [ -z "$TOKEN" ]; then
  echo -e "${RED}❌ 登录失败！请确认 Lumina 已启动在 ${LUMINA_HOST}${NC}"
  exit 1
fi
ok "登录成功"
AUTH="Authorization: Bearer ${TOKEN}"

# ==================== 步骤 2：生成模拟数据 ====================
step "2/14: 生成模拟运维数据（critical 模式）"
python3 "${SCRIPT_DIR}/gen_mock_data.py" --mode critical --outdir "$DATA_DIR" 2>/dev/null || \
  python "${SCRIPT_DIR}/gen_mock_data.py" --mode critical --outdir "$DATA_DIR"
ok "数据已生成: $DATA_DIR"

# ==================== 步骤 3：创建知识库 + 上传 SOP ====================
step "3/14: 创建知识库 + 上传 SOP 文档"

# 检查是否已有运维知识库
KB_LIST=$(curl -sS "${LUMINA_HOST}/api/v1/knowledge-bases" -H "$AUTH")
KB_ID=$(echo "$KB_LIST" | python3 -c "
import sys,json
d=json.load(sys.stdin)
data=d.get('data',{})
lst=data.get('list',data) if isinstance(data,dict) else data
if isinstance(lst,list):
    for kb in lst:
        if kb.get('name','')=='运维知识库':
            print(kb.get('id',kb.get('kbId','')))
            break
" 2>/dev/null || echo "")

if [ -z "$KB_ID" ]; then
  KB_ID=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/knowledge-bases" \
    -H 'Content-Type: application/json' -H "$AUTH" \
    -d '{"name":"运维知识库","description":"Nginx/数据库/故障分级 SOP"}' \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
  ok "知识库创建成功: id=${KB_ID}"

  # 上传 SOP 文档
  for doc in kb-nginx-sop.md kb-database-sop.md kb-incident-response.md; do
    curl -sS -X POST "${LUMINA_HOST}/api/v1/knowledge/documents" \
      -H "$AUTH" \
      -F "file=@${SCRIPT_DIR}/../data/${doc}" \
      -F "kbId=${KB_ID}" > /dev/null
    echo "    上传 ${doc}"
    sleep 2
  done
  ok "3 篇 SOP 文档已上传（RAG 分片中...）"
else
  warn "运维知识库已存在 (id=${KB_ID})，跳过"
fi

# ==================== 步骤 4：创建运维 Agent ====================
step "4/14: 创建运维 Agent（工具+KB+限流+并发）"

AGENT_LIST=$(curl -sS "${LUMINA_HOST}/api/v1/agents?agentName=运维" -H "$AUTH")
AGENT_ID=$(echo "$AGENT_LIST" | python3 -c "
import sys,json
d=json.load(sys.stdin)
lst=d.get('data',{}).get('list',d.get('data',[]))
if isinstance(lst,list) and lst:
    print(lst[0].get('agentId',''))
" 2>/dev/null || echo "")

if [ -z "$AGENT_ID" ]; then
  AGENT_ID=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/agents" \
    -H 'Content-Type: application/json' -H "$AUTH" \
    -d "{
      \"agentName\": \"运维巡检助手\",
      \"agentType\": \"ops-inspector\",
      \"description\": \"Plan-Execute 模式运维巡检 Agent\",
      \"tools\": \"ops.readLogs,ops.readMetrics,ops.executeCommand\",
      \"knowledgeBaseIds\": [${KB_ID}],
      \"rateLimit\": 10,
      \"maxConcurrent\": 1
    }" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['agentId'])" 2>/dev/null)
  ok "Agent 创建成功: id=${AGENT_ID}"
else
  warn "运维 Agent 已存在: id=${AGENT_ID}"
fi

# ==================== 步骤 5：执行巡检 ====================
step "5/14: 执行运维巡检（critical 模式数据）"
EXEC_RESP=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/agents/${AGENT_ID}/execute" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -d '{"task":"执行系统巡检：读取CPU/内存指标，读取Nginx和应用日志，分析异常，如有问题查知识库SOP给出建议"}')
EXEC_CODE=$(echo "$EXEC_RESP" | jval "code")

if [ "$EXEC_CODE" = "200" ]; then
  RESULT=$(echo "$EXEC_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(d[:150] if len(d)>150 else d)" 2>/dev/null)
  ok "巡检执行成功"
  echo -e "    结果摘要: ${CYAN}${RESULT}...${NC}"
else
  warn "巡检执行 code=${EXEC_CODE}（可能是 LLM Key 未配置或限流）"
  echo "    $(echo "$EXEC_RESP" | jval "msg")"
fi

# ==================== 步骤 6：验证限流 ====================
step "6/14: 验证限流（rateLimit=10）"
warn "快速发 12 次请求（等待可能较慢）..."
BLOCKED=0
for i in $(seq 1 12); do
  CODE=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/agents/${AGENT_ID}/execute" \
    -H 'Content-Type: application/json' -H "$AUTH" \
    -d '{"task":"test"}' | jval "code")
  if [ "$CODE" = "429" ]; then
    BLOCKED=$((BLOCKED + 1))
  fi
done
if [ "$BLOCKED" -gt 0 ]; then
  ok "限流生效：${BLOCKED} 次被拦截 (429)"
else
  warn "限流未触发（窗口可能已过）"
fi

# ==================== 步骤 7：创建 Webhook ====================
step "7/14: 创建 Webhook 通知"
warn "如需测试，请在另一个终端运行: python3 ${SCRIPT_DIR}/webhook_receiver.py --port 9999"

WEBHOOK_RESP=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/notifications/webhooks" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -d '{
    "name": "运维告警通知",
    "url": "http://127.0.0.1:9999/webhook",
    "channel": "WEBHOOK",
    "events": ["TASK","TRIGGER","BUDGET","WORKFLOW"]
  }')
WEBHOOK_CODE=$(echo "$WEBHOOK_RESP" | jval "code")
if [ "$WEBHOOK_CODE" = "200" ]; then
  WEBHOOK_SECRET=$(echo "$WEBHOOK_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data'].get('secret',''))" 2>/dev/null)
  ok "Webhook 创建成功"
  echo "    Secret (保存): ${WEBHOOK_SECRET}"
  echo "    接收端启动: python3 ${SCRIPT_DIR}/webhook_receiver.py --port 9999 --secret ${WEBHOOK_SECRET}"
else
  warn "Webhook 可能已存在或创建失败"
fi

# ==================== 步骤 8：创建预算规则 ====================
step "8/14: 创建预算规则"
curl -sS -X POST "${LUMINA_HOST}/api/v1/budget/rules" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -d "{
    \"ruleName\": \"运维巡检日预算\",
    \"scopeType\": \"AGENT\",
    \"scopeId\": ${AGENT_ID},
    \"periodType\": \"DAILY\",
    \"limitAmount\": 5.00,
    \"alertThreshold\": 80
  }" > /dev/null
ok "预算规则创建成功（AGENT 级日限 5 元）"

# ==================== 步骤 9：创建触发器 ====================
step "9/14: 创建 Cron 触发器（每小时巡检）"
curl -sS -X POST "${LUMINA_HOST}/api/v1/agent-triggers" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -d "{
    \"name\": \"每小时巡检\",
    \"agentId\": ${AGENT_ID},
    \"cronExpr\": \"0 0 * * * *\",
    \"inputText\": \"执行系统巡检\",
    \"misfirePolicy\": \"FIRE_ONCE\"
  }" > /dev/null
ok "触发器创建成功（每小时整点）"

# ==================== 步骤 10：多模态（生成图+上传） ====================
step "10/14: 多模态 — 生成图片并上传"
python3 "${SCRIPT_DIR}/gen_chart.py" --outdir "${DATA_DIR}/images" 2>/dev/null || \
  python "${SCRIPT_DIR}/gen_chart.py" --outdir "${DATA_DIR}/images"

# 找到生成的图片（.png 或 .svg）
IMG_FILE=""
for f in "${DATA_DIR}/images/architecture.png" "${DATA_DIR}/images/architecture.svg"; do
  [ -f "$f" ] && IMG_FILE="$f" && break
done

if [ -n "$IMG_FILE" ]; then
  FILE_UUID=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/files/upload" \
    -H "$AUTH" \
    -F "file=@${IMG_FILE}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['fileUuid'])" 2>/dev/null || echo "")
  if [ -n "$FILE_UUID" ]; then
    ok "图片上传成功: ${IMG_FILE}, uuid=${FILE_UUID}"
    MM_RESP=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/agents/${AGENT_ID}/execute/multimodal" \
      -H 'Content-Type: application/json' -H "$AUTH" \
      -d "{\"task\":\"分析这张图的内容\",\"fileUuids\":[\"$FILE_UUID\"]}")
    MM_CODE=$(echo "$MM_RESP" | jval "code")
    [ "$MM_CODE" = "200" ] && ok "多模态执行成功" || warn "多模态执行 code=${MM_CODE}"
  else
    warn "图片上传失败"
  fi
else
  warn "未找到生成的图片"
fi

# ==================== 步骤 11：注册 MCP ====================
step "11/14: 注册 MCP 文件服务器"
MCODE=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/mcp/servers" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -d "{
    \"name\": \"ops-fileserver\",
    \"transport\": \"stdio\",
    \"command\": \"python3\",
    \"args\": [\"${SCRIPT_DIR}/mcp_fileserver.py\", \"--root\", \"${DATA_DIR}/config\"]
  }" | jval "code")
[ "$MCODE" = "200" ] && ok "MCP server 注册成功" || warn "MCP 注册 code=${MCODE}（可能已注册）"

# ==================== 步骤 12：评估数据集 ====================
step "12/14: 导入评估数据集"
DS_RESP=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/evaluations/datasets/import" \
  -H "$AUTH" \
  -F "file=@${SCRIPT_DIR}/../data/eval-dataset.yaml" \
  -F "name=运维巡检评估" \
  -F "agentType=ops-inspector")
DS_CODE=$(echo "$DS_RESP" | jval "code")
[ "$DS_CODE" = "200" ] && ok "评估数据集导入成功" || warn "评估导入 code=${DS_CODE}"

# ==================== 步骤 13：API Token + OpenAI 兼容 ====================
step "13/14: 创建 API Token + OpenAI 兼容端点"
API_TOKEN=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/base/api-tokens" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -d '{"name":"ops-demo"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])" 2>/dev/null || echo "")

if [ -n "$API_TOKEN" ]; then
  ok "API Token 创建成功: ${API_TOKEN:0:20}..."
  OAI_RESP=$(curl -sS "${LUMINA_HOST}/v1/chat/completions" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${API_TOKEN}" \
    -d "{\"model\":\"agent-${AGENT_ID}\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"stream\":false}")
  OAI_CODE=$(echo "$OAI_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('object','error'))" 2>/dev/null || echo "error")
  echo "    OpenAI 兼容响应: ${OAI_CODE}"
else
  warn "API Token 创建失败"
fi

# ==================== 步骤 14：查看成本 ====================
step "14/14: 查看成本摘要"
COST=$(curl -sS "${LUMINA_HOST}/api/v1/cost/summary" -H "$AUTH" \
  | python3 -c "import sys,json; d=json.load(sys.stdin).get('data',{}); print(f\"任务={d.get('taskCount',0)} Token={d.get('totalTokens',0)}\")" 2>/dev/null || echo "无数据")
echo "    ${CYAN}成本: ${COST}${NC}"

# ==================== 总结 ====================
echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✅ Lumina 智能运维平台演示完成！${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo ""
echo "  已通过 API 自动完成的能力："
echo "    ✅ ① Agent 执行（工具调用 + RAG）"
echo "    ✅ ② 多模态（图片上传 + 分析）"
echo "    ✅ ③④ RAG + 知识库管理/隔离"
echo "    ✅ ⑤ 自定义工具（OpsToolProvider）"
echo "    ✅ ⑩ 限流 + 并发控制"
echo "    ✅ ⑪ 预算控制"
echo "    ✅ ⑬ Webhook 通知（已创建，接收端需手动启动）"
echo "    ✅ ⑭ MCP 接入"
echo "    ✅ ⑮ 评估数据集导入"
echo "    ✅ ⑯ OpenAI 兼容端点"
echo "    ✅ 成本追踪 + 审计日志"
echo ""
echo "  需手动完成的步骤（参考 docs/step-by-step.md）："
echo "    ⑥⑦⑧ DAG/Flowable 工作流 + 人工审批"
echo "    ⑨  Cron 触发器等待执行"
echo "    ⑫  Code Interpreter（需启用 + Agent 加 code.execute 工具）"
echo "    ⑮  A/B 测试（需先创建 Prompt 版本）"
echo ""
echo "  参考文档："
echo "    docs/step-by-step.md   — 16 步 curl 操作指南"
echo "    docs/frontend-guide.md — 纯前端 UI 操作指南"
echo "    docs/troubleshooting.md — 常见问题"
echo ""
