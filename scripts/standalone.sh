#!/usr/bin/env bash
# Lumina Standalone 操作脚本
#
# 用法：
#   ./scripts/standalone.sh build     # 构建 standalone jar
#   ./scripts/standalone.sh start     # 启动（后台，日志输出到 logs/standalone.log）
#   ./scripts/standalone.sh stop      # 停止
#   ./scripts/standalone.sh restart   # 重启
#   ./scripts/standalone.sh status    # 查看状态（进程 + health）
#   ./scripts/standalone.sh logs      # 实时查日志（tail -f）
#   ./scripts/standalone.sh clean     # 清理日志 + 临时文件（不动 jar）
#
# 前置：本机已起 MySQL（3306）+ Redis（6379）+ Qdrant（6333，可选）
# 配置：编辑 .env.standalone 或用环境变量覆盖
#
# 示例：
#   LLM_API_KEY=xxx LLM_TYPE=glm LLM_MODEL=glm-4-flash ./scripts/standalone.sh start

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR_PATH="$PROJECT_ROOT/lumina-standalone/target/lumina-standalone-1.0.0-SNAPSHOT.jar"
LOG_DIR="$PROJECT_ROOT/logs"
LOG_FILE="$LOG_DIR/standalone.log"
PID_FILE="$LOG_DIR/standalone.pid"
HEALTH_URL="http://localhost:8080/actuator/health"

mkdir -p "$LOG_DIR"

# 加载 .env.standalone（如果存在）
ENV_FILE="$PROJECT_ROOT/.env.standalone"
if [ -f "$ENV_FILE" ]; then
    set -a
    source "$ENV_FILE"
    set +a
fi

cmd="${1:-status}"

is_running() {
    if [ -f "$PID_FILE" ]; then
        local pid
        pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            return 0
        fi
    fi
    # fallback：用 jps 找
    if jps 2>/dev/null | grep -q "lumina-standalone"; then
        return 0
    fi
    return 1
}

get_pid() {
    if [ -f "$PID_FILE" ]; then
        cat "$PID_FILE"
    else
        jps 2>/dev/null | grep "lumina-standalone" | awk '{print $1}' | head -1
    fi
}

case "$cmd" in
    build)
        echo "📦 构建 standalone jar..."
        cd "$PROJECT_ROOT"
        mvn -pl lumina-standalone -am package -DskipTests -q
        echo "✅ 构建完成: $JAR_PATH"
        ls -lh "$JAR_PATH"
        ;;

    start)
        if is_running; then
            echo "⚠️  standalone 已在运行 (PID=$(get_pid))"
            exit 1
        fi
        if [ ! -f "$JAR_PATH" ]; then
            echo "❌ jar 不存在，先执行: $0 build"
            exit 1
        fi
        echo "🚀 启动 standalone..."
        echo "   jar: $JAR_PATH"
        echo "   log: $LOG_FILE"
        echo "   health: $HEALTH_URL"
        nohup java -jar "$JAR_PATH" > "$LOG_FILE" 2>&1 &
        PID=$!
        echo "$PID" > "$PID_FILE"
        disown 2>/dev/null || true
        echo "   PID: $PID"
        echo "   等启动 60 秒..."
        sleep 60
        if curl -s -m 5 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
            echo "✅ 启动成功: $HEALTH_URL = UP"
            echo "   访问 http://localhost:8080，admin / admin123"
        else
            echo "⚠️  60 秒后 health 未 UP，查日志: $0 logs"
            echo "   可能还在启动中（首次 Flyway 慢），再等 30 秒后查 status"
        fi
        ;;

    stop)
        if ! is_running; then
            echo "ℹ️  standalone 未在运行"
            rm -f "$PID_FILE"
            exit 0
        fi
        PID=$(get_pid)
        echo "🛑 停止 standalone (PID=$PID)..."
        kill "$PID" 2>/dev/null || true
        # 等优雅退出
        for i in $(seq 1 10); do
            if ! kill -0 "$PID" 2>/dev/null; then break; fi
            sleep 1
        done
        # 还没退出，强杀
        if kill -0 "$PID" 2>/dev/null; then
            echo "   优雅退出超时，强杀..."
            kill -9 "$PID" 2>/dev/null || true
        fi
        rm -f "$PID_FILE"
        echo "✅ 已停止"
        ;;

    restart)
        "$0" stop
        sleep 2
        "$0" start
        ;;

    status)
        if is_running; then
            PID=$(get_pid)
            echo "✅ standalone 运行中 (PID=$PID)"
            HEALTH=$(curl -s -m 3 "$HEALTH_URL" 2>/dev/null || echo "连接失败")
            echo "   health: $HEALTH"
        else
            echo "❌ standalone 未运行"
        fi
        ;;

    logs)
        if [ ! -f "$LOG_FILE" ]; then
            echo "❌ 日志不存在: $LOG_FILE"
            exit 1
        fi
        echo "📜 实时日志（Ctrl+C 退出）: $LOG_FILE"
        tail -f "$LOG_FILE"
        ;;

    clean)
        echo "🧹 清理日志和临时文件..."
        rm -rf "$LOG_DIR"/*.log "$LOG_DIR"/*.pid
        rm -rf /tmp/lumina-* /tmp/agent-* /tmp/openai-* /tmp/rag-* /tmp/wf-* /tmp/trigger-* 2>/dev/null
        echo "✅ 清理完成（jar 未动）"
        ;;

    *)
        echo "用法: $0 {build|start|stop|restart|status|logs|clean}"
        echo ""
        echo "命令说明："
        echo "  build    构建 standalone jar"
        echo "  start    后台启动（日志输出到 logs/standalone.log）"
        echo "  stop     停止（优雅退出 + 强杀兜底）"
        echo "  restart  重启"
        echo "  status   查看运行状态 + health"
        echo "  logs     实时查日志（tail -f）"
        echo "  clean    清理日志和临时文件"
        exit 1
        ;;
esac
