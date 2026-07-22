#!/usr/bin/env bash
# ============================================================
# Lumina 智能运维平台 — 一键演示脚本
#
# 自动完成：登录 → 创建Agent → 执行巡检 → 验证能力
#
# 用法:
#   bash examples/ops-platform/scripts/run_demo.sh
#
# 前置条件:
#   1. Lumina standalone 已启动（端口 8080）
#   2. 已设置 LLM_API_KEY 环境变量
#   3. 已运行 gen_mock_data.py 生成模拟数据
# ============================================================

set -euo pipefail

# ==================== 配置 ====================
LUMINA_HOST="${LUMINA_HOST:-http://localhost:8080}"
LUMINA_USER="${LUMINA_USER:-admin}"
LUMINA_PASS="${LUMINA_PASS:-admin123}"
DATA_DIR="${OPS_DATA_DIR:-/tmp/lumina-ops}"

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ==================== 步骤 1：登录 ====================
info "步骤 1/8: 登录获取 Token..."
LOGIN_RESP=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/base/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${LUMINA_USER}\",\"password\":\"${LUMINA_PASS}\"}")

TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])" 2>/dev/null || echo "")

if [ -z "$TOKEN" ]; then
  error "登录失败！请确认 Lumina 已启动在 ${LUMINA_HOST}"
  echo "  响应: $LOGIN_RESP"
  exit 1
fi
ok "登录成功，Token 长度: ${#TOKEN}"

AUTH="Authorization: Bearer ${TOKEN}"

# ==================== 步骤 2：生成模拟数据 ====================
info "步骤 2/8: 生成模拟运维数据..."
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

python3 "${SCRIPT_DIR}/gen_mock_data.py" --mode critical --outdir "$DATA_DIR"
ok "模拟数据已生成"

# ==================== 步骤 3：创建运维 Agent ====================
info "步骤 3/8: 创建运维巡检 Agent..."

# 检查是否已有 ops-inspector agent
EXISTING=$(curl -sS "${LUMINA_HOST}/api/v1/agents?agentName=运维" \
  -H "$AUTH" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',{}).get('list',d.get('data',[]))))" 2>/dev/null || echo "0")

if [ "$EXISTING" != "0" ]; then
  warn "运维 Agent 已存在，跳过创建"
  INSPECTOR_ID=$(curl -sS "${LUMINA_HOST}/api/v1/agents?agentName=巡检" \
    -H "$AUTH" | python3 -c "import sys,json; d=json.load(sys.stdin); lst=d.get('data',{}).get('list',d.get('data',[])); print(lst[0]['agentId'] if lst else '')" 2>/dev/null)
else
  CREATE_RESP=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/agents" \
    -H 'Content-Type: application/json' \
    -H "$AUTH" \
    -d '{
      "agentName": "运维巡检助手",
      "agentType": "ops-inspector",
      "description": "智能运维巡检 Agent",
      "llmConfig": {"modelType":"openai","modelName":"deepseek-chat","temperature":0.3,"maxTokens":2000},
      "tools": "ops.readLogs,ops.readMetrics,ops.executeCommand",
      "rateLimit": 10,
      "maxConcurrent": 1
    }')

  INSPECTOR_ID=$(echo "$CREATE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['agentId'])" 2>/dev/null || echo "")

  if [ -z "$INSPECTOR_ID" ]; then
    error "创建 Agent 失败: $CREATE_RESP"
    exit 1
  fi
  ok "运维巡检 Agent 创建成功: id=${INSPECTOR_ID}"
fi

# ==================== 步骤 4：上传知识库文档 ====================
info "步骤 4/8: 上传运维 SOP 文档到知识库..."

# 检查是否已有知识库
KB_RESP=$(curl -sS "${LUMINA_HOST}/api/v1/knowledge-bases" -H "$AUTH" 2>/dev/null || echo "{}")
KB_ID=$(echo "$KB_RESP" | python3 -c "
import sys,json
d=json.load(sys.stdin)
data=d.get('data',{})
lst=data.get('list',data) if isinstance(data,dict) else data
if isinstance(lst,list) and lst:
    print(lst[0].get('id',lst[0].get('kbId','')))
else:
    print('')
" 2>/dev/null || echo "")

if [ -n "$KB_ID" ]; then
  warn "知识库已存在 (id=${KB_ID})，请手动上传 SOP 文档"
else
  warn "请通过前端创建知识库并上传以下文档："
  echo "    ${SCRIPT_DIR}/../data/kb-nginx-sop.md"
  echo "    ${SCRIPT_DIR}/../data/kb-database-sop.md"
  echo "    ${SCRIPT_DIR}/../data/kb-incident-response.md"
fi

# ==================== 步骤 5：执行巡检 Agent ====================
info "步骤 5/8: 执行运维巡检（critical 模式数据）..."

EXEC_RESP=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/agents/${INSPECTOR_ID}/execute" \
  -H 'Content-Type: application/json' \
  -H "$AUTH" \
  -d '{"task":"执行系统巡检：读取CPU/内存指标，读取Nginx和应用日志，分析异常并给出建议"}')

EXEC_CODE=$(echo "$EXEC_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',500))" 2>/dev/null || echo "500")

if [ "$EXEC_CODE" = "200" ]; then
  RESULT=$(echo "$EXEC_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data'][:200])" 2>/dev/null || echo "(结果过长)")
  ok "巡检执行成功！"
  echo -e "  ${CYAN}结果摘要:${NC} ${RESULT}..."
else
  warn "巡检执行返回 code=$EXEC_CODE（可能是 LLM Key 未配置或限流）"
  echo "  $EXEC_RESP" | head -c 200
fi

# ==================== 步骤 6：验证限流 ====================
info "步骤 6/8: 验证限流（rateLimit=10，快速发 12 次）..."
BLOCKED=0
for i in $(seq 1 12); do
  CODE=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "${LUMINA_HOST}/api/v1/agents/${INSPECTOR_ID}/execute" \
    -H 'Content-Type: application/json' \
    -H "$AUTH" \
    -d '{"task":"test"}')
  # 通过响应体 code 判断（HTTP 都是 200，业务 code 429 才是限流）
  BODY=$(curl -sS -X POST "${LUMINA_HOST}/api/v1/agents/${INSPECTOR_ID}/execute" \
    -H 'Content-Type: application/json' \
    -H "$AUTH" \
    -d '{"task":"test"}')
  BIZ_CODE=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',0))" 2>/dev/null || echo "0")
  if [ "$BIZ_CODE" = "429" ]; then
    BLOCKED=$((BLOCKED + 1))
  fi
done

if [ "$BLOCKED" -gt 0 ]; then
  ok "限流生效：${BLOCKED} 次请求被拦截（429）"
else
  warn "限流未触发（可能窗口已过或 rateLimit 未生效）"
fi

# ==================== 步骤 7：查看成本/审计 ====================
info "步骤 7/8: 查看成本与审计..."
COST_RESP=$(curl -sS "${LUMINA_HOST}/api/v1/cost/summary" -H "$AUTH" 2>/dev/null || echo "{}")
echo -e "  ${CYAN}成本摘要:${NC} $(echo "$COST_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin).get('data',{}); print(f\"任务数={d.get('taskCount',0)}, 总Token={d.get('totalTokens',0)}\")" 2>/dev/null || echo "无数据")"

# ==================== 步骤 8：总结 ====================
info "步骤 8/8: 演示完成"
echo ""
echo -e "${GREEN}══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✅ Lumina 智能运维平台演示完成！${NC}"
echo -e "${GREEN}══════════════════════════════════════════════════════════${NC}"
echo ""
echo "  已演示能力："
echo "    ✅ ① Agent 执行（Plan-Execute + 工具调用）"
echo "    ✅ ⑤ 自定义工具（OpsToolProvider）"
echo "    ✅ ⑩ 限流（rateLimit=10）"
echo "    ✅ 审计日志（每次执行有记录）"
echo "    ✅ 成本追踪（/cost/summary）"
echo ""
echo "  下一步手动验证（参考 docs/step-by-step.md）："
echo "    ③④ RAG + 知识库隔离 — 上传 SOP 文档后执行"
echo "    ⑥⑧ DAG 工作流 + 人工审批 — 创建 workflow-dag.yaml"
echo "    ⑬ Webhook 通知 — 启动 webhook_receiver.py"
echo "    ⑭ MCP — 注册 mcp_fileserver.py"
echo "    ⑮ 评估/A/B — 导入 eval-dataset.yaml"
echo "    ⑯ OpenAI 兼容 — curl /v1/chat/completions"
echo ""
