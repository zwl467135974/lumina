#!/usr/bin/env python3
"""
Lumina 智能运维平台 — Webhook 接收端

接收 Lumina 平台推送的 Webhook 通知（告警/任务/触发器等），
验证 HMAC-SHA256 签名，格式化打印到终端。

用法:
    python3 webhook_receiver.py                       # 监听 0.0.0.0:9999
    python3 webhook_receiver.py --port 8088           # 自定义端口
    python3 webhook_receiver.py --secret my-secret    # 指定签名密钥

工作流：
    1. 在 Lumina 前端「通知管理 → Webhook」创建 Webhook
    2. URL 填 http://<本机IP>:9999/webhook
    3. Secret 填创建 Webhook 时返回的密钥（或用 --secret 指定的值）
    4. 启动此脚本，等待接收告警

无需任何第三方依赖，纯标准库实现。
"""

import argparse
import hashlib
import hmac
import json
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler


# 告警颜色（终端 ANSI 码）
COLORS = {
    "ERROR": "\033[91m",   # 红色
    "WARN": "\033[93m",    # 黄色
    "INFO": "\033[92m",    # 绿色
    "RESET": "\033[0m",
}


class WebhookHandler(BaseHTTPRequestHandler):
    """处理 Lumina Webhook POST 请求"""

    secret = None  # 由 main() 设置

    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length)

        # 验证 HMAC-SHA256 签名
        signature_header = self.headers.get("X-Lumina-Signature", "")
        if self.secret:
            expected = "sha256=" + hmac.new(
                self.secret.encode("utf-8"), body, hashlib.sha256
            ).hexdigest()
            if not hmac.compare_digest(signature_header, expected):
                print(f"\n{'='*60}")
                print(f"❌ 签名验证失败！")
                print(f"   收到的签名: {signature_header}")
                print(f"   期望的签名: {expected}")
                self.send_response(401)
                self.end_headers()
                self.wfile.write(b'{"error":"invalid signature"}')
                return

        # 解析通知内容
        try:
            event = json.loads(body.decode("utf-8"))
        except json.JSONDecodeError:
            event = {"raw": body.decode("utf-8", errors="replace")}

        # 格式化输出
        category = self.headers.get("X-Lumina-Event", event.get("category", "UNKNOWN"))
        severity = event.get("severity", "INFO")
        color = COLORS.get(severity, COLORS["INFO"])

        print(f"\n{'='*60}")
        print(f"{color}🔔 收到 Lumina 通知 [{severity}]{COLORS['RESET']}")
        print(f"{'='*60}")
        print(f"  时间:   {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"  类别:   {category}")
        print(f"  标题:   {event.get('title', 'N/A')}")
        print(f"  内容:   {event.get('content', 'N/A')}")
        print(f"  严重度: {severity}")
        if event.get("refType"):
            print(f"  关联:   {event['refType']}#{event.get('refId', '')}")
        print(f"  签名:   {'✅ 验证通过' if self.secret else '⚠️ 未配置签名验证'}")
        print(f"{'='*60}")

        # 返回 200 OK
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"success":true}')

    def do_GET(self):
        """健康检查端点"""
        if self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"OK")
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        """覆盖默认日志（保持终端干净）"""
        pass


def main():
    parser = argparse.ArgumentParser(description="Lumina Webhook 接收端")
    parser.add_argument("--port", type=int, default=9999, help="监听端口（默认 9999）")
    parser.add_argument("--host", default="0.0.0.0", help="监听地址（默认 0.0.0.0）")
    parser.add_argument("--secret", default=None, help="HMAC 签名密钥（与 Lumina Webhook 配置一致）")
    args = parser.parse_args()

    WebhookHandler.secret = args.secret

    server = HTTPServer((args.host, args.port), WebhookHandler)

    print(f"""
╔══════════════════════════════════════════════════════════╗
║         Lumina Webhook 接收端已启动                       ║
╠══════════════════════════════════════════════════════════╣
║  监听地址:  http://{args.host}:{args.port}                 ║
║  Webhook URL: http://<本机IP>:{args.port}/webhook         ║
║  健康检查:  http://<本机IP>:{args.port}/health            ║
║  签名验证:  {'✅ 已启用' if args.secret else '❌ 未启用'}{'':22}║
╠══════════════════════════════════════════════════════════╣
║  在 Lumina「通知管理 → Webhook」中配置：                  ║
║    URL:    http://<本机IP>:{args.port}                    ║
║    Secret: {args.secret or '(Lumina 会自动生成，复制到 --secret)'}{"":<16}║
╚══════════════════════════════════════════════════════════╝
""")

    print("等待接收通知... (Ctrl+C 退出)\n")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n\n已停止。")
        server.shutdown()


if __name__ == "__main__":
    main()
