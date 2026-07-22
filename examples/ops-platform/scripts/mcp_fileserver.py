#!/usr/bin/env python3
"""
Lumina 智能运维平台 — 简易 MCP 文件服务器 (stdio transport)

提供文件读取能力，让 Agent 通过 MCP 协议读写运维配置文件。

工具:
  - list_files: 列出指定目录下的文件
  - read_file:  读取指定文件内容

用法:
    python3 mcp_fileserver.py                         # 默认根目录 /tmp/lumina-ops/config
    python3 mcp_fileserver.py --root /etc/nginx       # 自定义根目录

在 Lumina 中注册（application.yml 或 API）:
    lumina.mcp.enabled: true
    lumina.mcp.servers:
      - name: ops-fileserver
        transport: stdio
        command: python3
        args:
          - "examples/ops-platform/scripts/mcp_fileserver.py"
          - "--root"
          - "/tmp/lumina-ops/config"

或通过 API 动态注册:
    POST /api/v1/mcp/servers
    {
      "name": "ops-fileserver",
      "transport": "stdio",
      "command": "python3",
      "args": ["examples/ops-platform/scripts/mcp_fileserver.py"]
    }

注册后 Agent 可调用 mcp__ops-fileserver__list_files / mcp__ops-fileserver__read_file。

无需任何第三方依赖，纯标准库实现 JSON-RPC over stdio。
"""

import argparse
import json
import os
import sys


class McpServer:
    """最简 MCP stdio server — JSON-RPC 2.0"""

    def __init__(self, root_dir):
        self.root_dir = os.path.abspath(root_dir)
        self.tools = [
            {
                "name": "list_files",
                "description": "列出指定目录下的文件名列表。dir 参数是相对于根目录的路径（默认根目录）。",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "dir": {"type": "string", "description": "目录路径（相对根目录），默认 '.'"},
                    },
                },
            },
            {
                "name": "read_file",
                "description": "读取指定文件的内容。path 参数是相对于根目录的文件路径。",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string", "description": "文件路径（相对根目录）"},
                    },
                    "required": ["path"],
                },
            },
        ]

    def handle_request(self, request):
        """处理 JSON-RPC 请求"""
        method = request.get("method", "")
        req_id = request.get("id")
        params = request.get("params", {})

        if method == "initialize":
            return self._result(req_id, {
                "protocolVersion": "2024-11-05",
                "capabilities": {"tools": {}},
                "serverInfo": {"name": "ops-fileserver", "version": "1.0.0"},
            })

        elif method == "notifications/initialized":
            return None  # 通知不需要响应

        elif method == "tools/list":
            return self._result(req_id, {"tools": self.tools})

        elif method == "tools/call":
            tool_name = params.get("name")
            args = params.get("arguments", {})

            if tool_name == "list_files":
                return self._tool_result(req_id, self.list_files(args.get("dir", ".")))
            elif tool_name == "read_file":
                return self._tool_result(req_id, self.read_file(args.get("path", "")))
            else:
                return self._error(req_id, -32601, f"Unknown tool: {tool_name}")

        elif method == "ping":
            return self._result(req_id, {})

        else:
            return self._error(req_id, -32601, f"Unknown method: {method}")

    def list_files(self, dir_path):
        """列出目录文件"""
        full_path = os.path.join(self.root_dir, dir_path)
        full_path = os.path.normpath(full_path)

        # 安全检查：不允许路径逃逸
        if not full_path.startswith(self.root_dir):
            return [{"type": "text", "text": f"错误: 路径超出根目录范围"}]

        if not os.path.isdir(full_path):
            return [{"type": "text", "text": f"错误: 目录不存在: {dir_path}"}]

        entries = []
        for name in sorted(os.listdir(full_path)):
            fp = os.path.join(full_path, name)
            if os.path.isfile(fp):
                size = os.path.getsize(fp)
                entries.append(f"📄 {name} ({size} bytes)")
            elif os.path.isdir(fp):
                entries.append(f"📁 {name}/")

        result = f"目录: {dir_path}\n{'─' * 40}\n" + "\n".join(entries)
        return [{"type": "text", "text": result}]

    def read_file(self, file_path):
        """读取文件内容"""
        full_path = os.path.join(self.root_dir, file_path)
        full_path = os.path.normpath(full_path)

        if not full_path.startswith(self.root_dir):
            return [{"type": "text", "text": "错误: 路径超出根目录范围"}]

        if not os.path.isfile(full_path):
            return [{"type": "text", "text": f"错误: 文件不存在: {file_path}"}]

        try:
            with open(full_path, "r", encoding="utf-8", errors="replace") as f:
                content = f.read(10000)  # 限制 10KB
            return [{"type": "text", "text": content}]
        except Exception as e:
            return [{"type": "text", "text": f"读取失败: {e}"}]

    def _result(self, req_id, result):
        return {"jsonrpc": "2.0", "id": req_id, "result": result}

    def _error(self, req_id, code, message):
        return {"jsonrpc": "2.0", "id": req_id, "error": {"code": code, "message": message}}

    def _tool_result(self, req_id, content):
        return {"jsonrpc": "2.0", "id": req_id, "result": {"content": content}}

    def run(self):
        """主循环：从 stdin 读取 JSON-RPC，向 stdout 写响应"""
        for line in sys.stdin:
            line = line.strip()
            if not line:
                continue

            try:
                request = json.loads(line)
            except json.JSONDecodeError:
                continue

            response = self.handle_request(request)
            if response is not None:
                sys.stdout.write(json.dumps(response) + "\n")
                sys.stdout.flush()


def main():
    parser = argparse.ArgumentParser(description="Lumina MCP 文件服务器 (stdio)")
    parser.add_argument("--root", default="/tmp/lumina-ops/config",
                        help="文件服务根目录（默认 /tmp/lumina-ops/config）")
    args = parser.parse_args()

    if not os.path.isdir(args.root):
        print(f"MCP fileserver: 根目录不存在 {args.root}，请先运行 gen_mock_data.py", file=sys.stderr)
        sys.exit(1)

    server = McpServer(args.root)
    server.run()


if __name__ == "__main__":
    main()
