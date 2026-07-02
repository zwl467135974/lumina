#!/usr/bin/env bash
set -euo pipefail

# Lumina 一键启动脚本（Linux / macOS）
# 用法: ./scripts/init.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo "=========================================="
echo "  Lumina AI Agent Platform - 初始化部署"
echo "=========================================="

# ---------- 1. 检查依赖 ----------
echo ""
echo "[1/6] 检查依赖..."

check_cmd() {
    if ! command -v "$1" &>/dev/null; then
        echo "  ❌ 未安装 $1，请先安装"
        exit 1
    fi
    echo "  ✅ $1: $(command -v "$1")"
}

check_cmd docker
check_cmd java
check_cmd mvn

# ---------- 2. 环境变量 ----------
echo ""
echo "[2/6] 配置环境变量..."

if [ ! -f .env ]; then
    cp .env.example .env
    echo "  ⚠️  已从模板生成 .env，请编辑填入真实密钥后重新运行"
    echo "  必须修改: MYSQL_ROOT_PASSWORD / LUMINA_JWT_SECRET / LLM_API_KEY / RAG_EMBEDDING_API_KEY"
    exit 0
else
    echo "  ✅ .env 已存在"
fi

# ---------- 3. 构建后端 ----------
echo ""
echo "[3/6] 构建后端（Maven）..."
mvn install -DskipTests -q
echo "  ✅ 后端构建完成"

# ---------- 4. 构建前端 ----------
echo ""
echo "[4/6] 构建前端（pnpm）..."
cd lumina-frontend
if [ ! -d node_modules ]; then
    pnpm install
fi
pnpm build
cd "$PROJECT_DIR"
echo "  ✅ 前端构建完成"

# ---------- 5. 启动 Docker 服务 ----------
echo ""
echo "[5/6] 启动 Docker Compose..."
docker compose up -d --build
echo "  ✅ 服务启动中"

# ---------- 6. 健康检查 ----------
echo ""
echo "[6/6] 等待服务就绪..."
sleep 15

check_health() {
    local name=$1 url=$2
    if curl -sf "$url" -o /dev/null 2>/dev/null; then
        echo "  ✅ $name"
    else
        echo "  ⏳ $name（启动中，请稍后检查）"
    fi
}

check_health "网关" "http://localhost:8080/actuator/health"
check_health "Jaeger UI" "http://localhost:16686"
check_health "Qdrant" "http://localhost:6333/healthz"

echo ""
echo "=========================================="
echo "  部署完成！"
echo "  前端:   http://localhost"
echo "  API:    http://localhost:8080"
echo "  追踪:   http://localhost:16686"
echo "  Grafana: http://localhost:3001"
echo "=========================================="
