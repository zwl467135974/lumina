#!/usr/bin/env python3
"""
Lumina 智能运维平台 — 模拟数据生成器

生成运维巡检所需的模拟数据（Nginx 日志、系统指标、应用错误日志），
写入本地目录供 OpsToolProvider 读取。

用法:
    python3 gen_mock_data.py                      # 默认 normal 模式
    python3 gen_mock_data.py --mode warning       # 警告级异常
    python3 gen_mock_data.py --mode critical      # P0 严重故障
    python3 gen_mock_data.py --outdir /tmp/ops    # 自定义输出目录

无需任何第三方依赖，纯标准库实现。
"""

import argparse
import json
import os
import random
import time
from datetime import datetime, timedelta


# ==================== 数据生成器 ====================

HTTP_STATUS_NORMAL = [200] * 88 + [301] * 5 + [304] * 5 + [404] * 2
HTTP_STATUS_WARNING = [200] * 65 + [301] * 3 + [404] * 10 + [499] * 7 + [500] * 8 + [502] * 5 + [503] * 2
HTTP_STATUS_CRITICAL = [200] * 30 + [500] * 20 + [502] * 25 + [503] * 15 + [504] * 10

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15",
    "curl/8.4.0",
    "Mozilla/5.0 (compatible; Googlebot/2.1)",
]

API_PATHS = [
    "/api/v1/agents", "/api/v1/agents/1/execute", "/api/v1/workflows",
    "/api/v1/knowledge-bases", "/api/v1/conversations", "/api/v1/cost/summary",
    "/v1/chat/completions", "/api/v1/files/upload", "/api/v1/auth/login",
    "/api/v1/dashboard/stats", "/api/v1/evaluations",
]

CLIENT_IPS = [
    "10.0.1.101", "10.0.1.102", "10.0.1.103", "10.0.2.201", "10.0.2.202",
    "172.16.0.51", "172.16.0.52", "192.168.1.10", "192.168.1.11",
]


def gen_nginx_log(mode, count=200):
    """生成 Nginx access log 格式的日志"""
    if mode == "normal":
        statuses = HTTP_STATUS_NORMAL
    elif mode == "warning":
        statuses = HTTP_STATUS_WARNING
    else:
        statuses = HTTP_STATUS_CRITICAL

    lines = []
    now = datetime.now()
    for i in range(count):
        ts = now - timedelta(seconds=count - i)
        ip = random.choice(CLIENT_IPS)
        method = random.choice(["GET", "GET", "GET", "POST", "POST", "PUT", "DELETE"])
        path = random.choice(API_PATHS)
        status = random.choice(statuses)
        body_bytes = random.randint(256, 8192) if status == 200 else random.randint(0, 512)
        response_time = round(random.uniform(0.01, 0.5), 3)
        if status >= 500:
            response_time = round(random.uniform(1.0, 5.0), 3)
        ua = random.choice(USER_AGENTS)

        line = (
            f'{ip} - - [{ts.strftime("%d/%b/%Y:%H:%M:%S")} +0800] '
            f'"{method} {path} HTTP/1.1" {status} {body_bytes} '
            f'"{response_time}" "-" "{ua}"'
        )
        lines.append(line)

    return "\n".join(lines) + "\n"


def gen_metrics(mode):
    """生成系统指标 JSON"""
    if mode == "normal":
        cpu = round(random.uniform(15, 45), 1)
        memory = round(random.uniform(30, 55), 1)
        disk = round(random.uniform(40, 60), 1)
        network_in = round(random.uniform(1.0, 5.0), 1)
        network_out = round(random.uniform(0.5, 3.0), 1)
    elif mode == "warning":
        cpu = round(random.uniform(65, 85), 1)
        memory = round(random.uniform(70, 85), 1)
        disk = round(random.uniform(75, 88), 1)
        network_in = round(random.uniform(10.0, 30.0), 1)
        network_out = round(random.uniform(5.0, 15.0), 1)
    else:
        cpu = round(random.uniform(90, 99.5), 1)
        memory = round(random.uniform(88, 97), 1)
        disk = round(random.uniform(90, 98), 1)
        network_in = round(random.uniform(50.0, 120.0), 1)
        network_out = round(random.uniform(30.0, 80.0), 1)

    now = datetime.now()
    history = []
    for i in range(11):
        t = now - timedelta(minutes=10 * (10 - i))
        offset = (10 - i) * 0.02
        history.append({
            "timestamp": t.strftime("%Y-%m-%dT%H:%M:%S"),
            "cpu": round(max(1, cpu * (1 - offset + random.uniform(-0.05, 0.05))), 1),
            "memory": round(max(1, memory * (1 - offset + random.uniform(-0.03, 0.03))), 1),
        })

    return {
        "timestamp": now.strftime("%Y-%m-%dT%H:%M:%S"),
        "host": "prod-web-01",
        "mode": mode,
        "cpu": {"usage_percent": cpu, "cores": 8, "load_1m": round(cpu / 100 * 8, 2)},
        "memory": {"usage_percent": memory, "total_gb": 32, "used_gb": round(32 * memory / 100, 1)},
        "disk": {"usage_percent": disk, "total_gb": 500, "used_gb": round(500 * disk / 100, 1)},
        "network": {"in_mbps": network_in, "out_mbps": network_out},
        "history": history,
    }


def gen_app_log(mode, count=50):
    """生成应用错误日志"""
    levels = ["DEBUG", "INFO", "WARN", "ERROR"]
    if mode == "normal":
        weights = [20, 60, 15, 5]
    elif mode == "warning":
        weights = [10, 40, 35, 15]
    else:
        weights = [5, 20, 30, 45]

    messages = {
        "DEBUG": [
            "Processing request on thread http-nio-8080-exec-{}",
            "Cache hit for key agent_config_{}",
            "Database connection borrowed from pool",
        ],
        "INFO": [
            "Agent 执行成功: id={}, duration={}ms",
            "Workflow node completed: type=agent, status=SUCCESS",
            "Scheduled task executed: triggerId={}",
        ],
        "WARN": [
            "Response time slow: {}ms for POST /api/v1/agents/{}/execute",
            "Connection pool usage high: {}/20 active",
            "Rate limit warning: agentId={}, count={}/10",
        ],
        "ERROR": [
            "Nginx upstream timeout: connection refused to backend 10.0.1.{}, port 8080",
            "OutOfMemoryError: Java heap space. Used {}MB / {}MB",
            "Database connection pool exhausted: all 20 connections in use",
            "Agent execution failed: LLM API timeout after 30000ms",
            "NullPointerException at AgentServiceImpl.executeAgentForResult(line:314)",
        ],
    }

    lines = []
    now = datetime.now()
    for i in range(count):
        ts = now - timedelta(seconds=count * 2 - i * 2)
        level = random.choices(levels, weights=weights)[0]
        msg_template = random.choice(messages[level])
        msg = msg_template.format(
            random.randint(1, 255),
            random.randint(1, 20),
            random.randint(50, 5000),
        )
        thread = f"http-nio-8080-exec-{random.randint(1, 10)}"
        trace_id = f"{random.randint(10000000, 99999999):x}"

        line = (
            f'{ts.strftime("%Y-%m-%d %H:%M:%S")} [{thread}] [{trace_id}] '
            f'{level}  i.l.a.AgentServiceImpl - {msg}'
        )
        lines.append(line)

    return "\n".join(lines) + "\n"


def gen_config_files(outdir):
    """生成模拟的运维配置文件（供 MCP server 读取）"""
    config_dir = os.path.join(outdir, "config")
    os.makedirs(config_dir, exist_ok=True)

    nginx_config = """# Nginx 反向代理配置 — prod-web-01
upstream backend {
    server 10.0.1.10:8080 max_fails=3 fail_timeout=30s;
    server 10.0.1.11:8080 max_fails=3 fail_timeout=30s;
    keepalive 32;
}

server {
    listen 80;
    server_name api.lumina.example.com;

    location /api/ {
        proxy_pass http://backend;
        proxy_connect_timeout 5s;
        proxy_read_timeout 30s;
        proxy_next_upstream error timeout http_502 http_503;
    }
}
"""
    with open(os.path.join(config_dir, "nginx.conf"), "w") as f:
        f.write(nginx_config)

    app_config = """# 应用配置 — prod-web-01
server.port=8080
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.connection-timeout=30000
spring.redis.timeout=5000
lumina.agent.rate-limit.max-requests=30
lumina.agent.rate-limit.window-seconds=60
management.endpoints.web.exposure.include=health,info,metrics
"""
    with open(os.path.join(config_dir, "application.properties"), "w") as f:
        f.write(app_config)


# ==================== 主逻辑 ====================

def main():
    parser = argparse.ArgumentParser(description="生成运维巡检模拟数据")
    parser.add_argument("--mode", choices=["normal", "warning", "critical"], default="normal",
                        help="数据模式：normal(正常) / warning(警告) / critical(严重)")
    parser.add_argument("--outdir", default="/tmp/lumina-ops",
                        help="输出目录（默认 /tmp/lumina-ops）")
    parser.add_argument("--log-count", type=int, default=200,
                        help="Nginx 日志条数（默认 200）")
    args = parser.parse_args()

    outdir = args.outdir
    os.makedirs(outdir, exist_ok=True)
    os.makedirs(os.path.join(outdir, "nginx"), exist_ok=True)
    os.makedirs(os.path.join(outdir, "metrics"), exist_ok=True)
    os.makedirs(os.path.join(outdir, "app"), exist_ok=True)

    # 生成数据
    nginx_log = gen_nginx_log(args.mode, args.log_count)
    with open(os.path.join(outdir, "nginx", "access.log"), "w") as f:
        f.write(nginx_log)

    metrics = gen_metrics(args.mode)
    with open(os.path.join(outdir, "metrics", "cpu.json"), "w") as f:
        json.dump(metrics, f, ensure_ascii=False, indent=2)

    app_log = gen_app_log(args.mode)
    with open(os.path.join(outdir, "app", "error.log"), "w") as f:
        f.write(app_log)

    gen_config_files(outdir)

    # 打印摘要
    print(f"✅ 模拟数据已生成: {outdir} (mode={args.mode})")
    print(f"   Nginx 日志: {outdir}/nginx/access.log ({args.log_count} 条)")
    print(f"   系统指标:   {outdir}/metrics/cpu.json (CPU {metrics['cpu']['usage_percent']}%, 内存 {metrics['memory']['usage_percent']}%)")
    print(f"   应用日志:   {outdir}/app/error.log")
    print(f"   配置文件:   {outdir}/config/")

    if args.mode == "critical":
        print("\n   ⚠️  CRITICAL 模式：注入了 502/503 错误、CPU 飙升、OOM 日志")
    elif args.mode == "warning":
        print("\n   ⚠️  WARNING 模式：注入了少量 5xx 错误、CPU 偏高")


if __name__ == "__main__":
    main()
