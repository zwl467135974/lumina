#!/usr/bin/env python3
"""
Lumina MCP Echo Server — 用于验证 Lumina 的 MCP 接入能力

提供一个 echo 工具（原样返回输入）和一个 ping 工具（返回 pong）。
用 stdio 传输，与 Lumina McpClientRegistry 的 stdio 模式对接。

启动方式（单独测试）:
    python echo_server.py

Lumina 配置方式（nacos-config/lumina-agent-service.yaml）:
    lumina:
      mcp:
        enabled: true
        servers:
          - name: echo
            transport: stdio
            command: python
            args:
              - "scripts/mcp/echo_server.py"

依赖: pip install mcp
"""

import sys
import json
from datetime import datetime


def main():
    """MCP stdio 主循环：读 stdin JSON-RPC，写 stdout JSON-RPC"""
    # MCP 协议：stdin/stdout 交换 JSON-RPC 2.0 消息
    # 每行一个 JSON 对象（newline-delimited JSON）

    initialized = False
    tools = [
        {
            "name": "echo",
            "description": "原样返回输入文本，用于验证 MCP 工具调用链路",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "text": {
                        "type": "string",
                        "description": "要回显的文本"
                    }
                },
                "required": ["text"]
            }
        },
        {
            "name": "ping",
            "description": "返回 pong 和当前服务器时间",
            "inputSchema": {
                "type": "object",
                "properties": {}
            }
        }
    ]

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue

        try:
            request = json.loads(line)
        except json.JSONDecodeError:
            continue

        req_id = request.get("id")
        method = request.get("method", "")

        # === JSON-RPC 方法分发 ===

        if method == "initialize":
            response = {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {
                        "tools": {}
                    },
                    "serverInfo": {
                        "name": "lumina-echo-server",
                        "version": "1.0.0"
                    }
                }
            }
            send(response)
            initialized = True

        elif method == "notifications/initialized":
            # 通知消息，不需要回复
            pass

        elif method == "tools/list":
            response = {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "tools": tools
                }
            }
            send(response)

        elif method == "tools/call":
            params = request.get("params", {})
            tool_name = params.get("name", "")
            args = params.get("arguments", {})

            if tool_name == "echo":
                text = args.get("text", "")
                result_text = f"echo: {text}"
            elif tool_name == "ping":
                result_text = f"pong at {datetime.now().isoformat()}"
            else:
                result_text = f"unknown tool: {tool_name}"

            response = {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "content": [
                        {"type": "text", "text": result_text}
                    ],
                    "isError": False
                }
            }
            send(response)

        elif method == "ping":
            response = {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {}
            }
            send(response)

        else:
            # 未知方法
            if req_id is not None:
                response = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "error": {
                        "code": -32601,
                        "message": f"Method not found: {method}"
                    }
                }
                send(response)


def send(obj):
    """发送 JSON-RPC 响应到 stdout"""
    print(json.dumps(obj), flush=True)


if __name__ == "__main__":
    main()
