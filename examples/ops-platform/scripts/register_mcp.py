import urllib.request, json, tempfile, os

config_dir = os.path.join(tempfile.gettempdir(), 'lumina-ops', 'config').replace(os.sep, '/')
token = open('/tmp/lumina_token.txt').read().strip()

url = 'http://localhost:8080/api/v1/mcp/servers'
data = json.dumps({
    'name': 'ops-fileserver3',
    'transport': 'stdio',
    'command': 'python',
    'args': ['examples/ops-platform/scripts/mcp_fileserver.py', '--root', config_dir]
}).encode('utf-8')
req = urllib.request.Request(url, data=data, method='POST')
req.add_header('Content-Type', 'application/json')
req.add_header('Authorization', f'Bearer {token}')
try:
    resp = urllib.request.urlopen(req)
    result = json.loads(resp.read())
    print(f"MCP 注册: code={result['code']}")
except Exception as e:
    print(f"MCP 注册失败: {e}")
