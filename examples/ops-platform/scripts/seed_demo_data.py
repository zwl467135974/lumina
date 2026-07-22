#!/usr/bin/env python3
"""
灌入完整演示数据：Agent + KB挂载 + 触发器 + Webhook + 工作流 + 预算 + Prompt
"""
import urllib.request
import json
import os
import time

HOST = "http://localhost:18080"
USER = "admin"
PASS = "admin123"

def api(method, path, data=None, token=None, is_json=True):
    url = HOST + path
    headers = {}
    body = None
    if data is not None:
        if is_json:
            body = json.dumps(data).encode('utf-8')
            headers['Content-Type'] = 'application/json'
        else:
            body = data
    if token:
        headers['Authorization'] = f'Bearer {token}'
    req = urllib.request.Request(url, data=body, method=method, headers=headers)
    try:
        resp = urllib.request.urlopen(req)
        return json.loads(resp.read())
    except Exception as e:
        print(f"  ERROR {method} {path}: {e}")
        return None

def upload_file(path, filepath, token, extra_fields=None):
    boundary = "----LuminaBoundary7MA4YWxkTrZu0gW"
    lines = []
    for key, val in (extra_fields or {}).items():
        lines.append(f'--{boundary}')
        lines.append(f'Content-Disposition: form-data; name="{key}"')
        lines.append('')
        lines.append(str(val))
    filename = os.path.basename(filepath)
    lines.append(f'--{boundary}')
    lines.append(f'Content-Disposition: form-data; name="file"; filename="{filename}"')
    lines.append('Content-Type: application/octet-stream')
    lines.append('')
    with open(filepath, 'rb') as f:
        file_content = f.read()
    lines.append('')
    body = '\r\n'.join(lines).encode('utf-8') + file_content + f'\r\n--{boundary}--\r\n'.encode('utf-8')
    req = urllib.request.Request(
        HOST + path, data=body, method='POST',
        headers={'Content-Type': f'multipart/form-data; boundary={boundary}',
                 'Authorization': f'Bearer {token}'})
    resp = urllib.request.urlopen(req)
    return json.loads(resp.read())


print("=== 1. 登录 ===")
r = api('POST', '/api/v1/base/auth/login', {'username': USER, 'password': PASS})
token = r['data']['token']
print(f"  Token: {token[:20]}...")

print("\n=== 2. 创建知识库 ===")
r = api('POST', '/api/v1/knowledge-bases', {'name': '运维知识库', 'description': 'Nginx/数据库/故障分级 SOP'}, token)
kb_id = r['data']['id'] if r and r.get('data') else None
if not kb_id:
    r2 = api('GET', '/api/v1/knowledge-bases', token=token)
    data = r2.get('data', {})
    lst = data.get('list', data) if isinstance(data, dict) else data
    kb_id = lst[0]['id'] if lst else None
print(f"  KB ID: {kb_id}")

print("\n=== 3. 上传 SOP 文档 ===")
base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for doc in ['kb-nginx-sop.md', 'kb-database-sop.md', 'kb-incident-response.md']:
    filepath = os.path.join(base, 'data', doc)
    if os.path.exists(filepath):
        r = upload_file('/api/v1/knowledge/documents', filepath, token, {'kbId': kb_id})
        print(f"  {doc}: {'OK' if r and r.get('code') == 200 else 'FAIL'}")
        time.sleep(1)

print("\n=== 4. 创建运维 Agent ===")
agent_data = {
    'agentName': '运维巡检助手',
    'agentType': 'ops-inspector',
    'description': 'Plan-Execute 模式运维巡检 Agent，自动读取系统指标和日志，结合知识库给出诊断建议',
    'tools': ['ops.readLogs', 'ops.readMetrics', 'ops.executeCommand'],
    'knowledgeBaseIds': [kb_id] if kb_id else [],
    'rateLimit': 10,
    'maxConcurrent': 1,
    'llmConfig': {'modelType': 'openai', 'modelName': 'deepseek-chat', 'temperature': 0.3, 'maxTokens': 2000}
}
r = api('POST', '/api/v1/agents', agent_data, token)
agent_id = r['data']['agentId'] if r and r.get('data') else None
if not agent_id:
    r2 = api('GET', '/api/v1/agents?agentName=运维', token=token)
    lst = r2.get('data', {}).get('list', r2.get('data', []))
    agent_id = lst[0]['agentId'] if lst else None
print(f"  Agent ID: {agent_id}")

print("\n=== 5. 创建运维报告 Agent ===")
agent2_data = {
    'agentName': '运维报告助手',
    'agentType': 'ops-reporter',
    'description': '根据巡检结果生成结构化运维报告',
    'rateLimit': 20,
    'maxConcurrent': 3,
}
r = api('POST', '/api/v1/agents', agent2_data, token)
agent2_id = r['data']['agentId'] if r and r.get('data') else None
print(f"  Report Agent ID: {agent2_id}")

print("\n=== 6. 创建告警通知 Agent ===")
agent3_data = {
    'agentName': '告警通知助手',
    'agentType': 'ops-alerter',
    'description': '将运维异常格式化为告警消息',
    'rateLimit': 20,
    'maxConcurrent': 5,
}
r = api('POST', '/api/v1/agents', agent3_data, token)
agent3_id = r['data']['agentId'] if r and r.get('data') else None
print(f"  Alert Agent ID: {agent3_id}")

print("\n=== 7. 创建 Cron 触发器 ===")
trig_data = {
    'name': '每小时系统巡检',
    'agentId': agent_id,
    'cronExpr': '0 0 * * * *',
    'inputText': '请执行系统健康巡检，检查所有关键指标和日志',
    'misfirePolicy': 'FIRE_ONCE'
}
r = api('POST', '/api/v1/agent-triggers', trig_data, token)
print(f"  Trigger: {'OK' if r and r.get('code') == 200 else 'SKIP (may exist)'}")

print("\n=== 8. 创建 Webhook ===")
wh_data = {
    'name': '运维告警通知',
    'url': 'http://127.0.0.1:9999/webhook',
    'channel': 'WEBHOOK',
    'events': ['TASK', 'TRIGGER', 'BUDGET', 'WORKFLOW']
}
r = api('POST', '/api/v1/notifications/webhooks', wh_data, token)
print(f"  Webhook: {'OK' if r and r.get('code') == 200 else 'SKIP (may exist)'}")

print("\n=== 9. 创建预算规则 ===")
budget_data = {
    'ruleName': '运维巡检日预算',
    'scopeType': 'AGENT',
    'scopeId': agent_id,
    'periodType': 'DAILY',
    'limitAmount': 10.00,
    'alertThreshold': 80
}
r = api('POST', '/api/v1/budget/rules', budget_data, token)
print(f"  Budget: {'OK' if r and r.get('code') == 200 else 'SKIP (may exist)'}")

print("\n=== 10. 创建 Prompt ===")
for name, content in [
    ('ops-inspector', '你是一名资深运维工程师，负责系统健康巡检。\n工作流程：\n1. 调用 ops.readMetrics 读取指标\n2. 调用 ops.readLogs 读取日志\n3. 分析异常\n4. 查知识库 SOP\n5. 输出报告'),
    ('ops-inspector-concise', '你是运维工程师，简洁输出巡检结果（3行）：状态/异常/严重度'),
    ('ops-inspector-detailed', '你是资深运维工程师，输出详细巡检报告（指标摘要/日志分析/异常发现/建议/严重度）'),
]:
    r = api('POST', '/api/v1/prompts', {'name': name, 'content': content, 'agentType': name, 'variables': 'task'}, token)
    if r and r.get('code') == 200:
        pid = r['data'].get('id') or r['data'].get('promptId')
        if pid:
            api('POST', f'/api/v1/prompts/{pid}/publish', token=token)
            print(f"  Prompt {name}: created + published")
    else:
        print(f"  Prompt {name}: SKIP (may exist)")

print("\n=== 11. 创建工作流 ===")
wf_yaml_path = os.path.join(base, 'config', 'workflow-dag.yaml')
if os.path.exists(wf_yaml_path):
    with open(wf_yaml_path, 'r') as f:
        wf_yaml = f.read()
    # 替换 agentId 占位值
    if agent_id:
        wf_yaml = wf_yaml.replace('agentId: 1', f'agentId: {agent_id}')
        wf_yaml = wf_yaml.replace('agentId: 2', f'agentId: {agent_id}')
        wf_yaml = wf_yaml.replace('agentId: 3', f'agentId: {agent_id}')
    r = api('POST', '/api/v1/workflows', {'name': '运维巡检流水线', 'description': '采集→分析→严重度判断→告警/报告', 'definitionYaml': wf_yaml}, token)
    if r and r.get('code') == 200:
        wf_id = r['data'].get('id') or r['data'].get('definitionId')
        if wf_id:
            api('POST', f'/api/v1/workflows/{wf_id}/publish', token=token)
            print(f"  Workflow: created + published (id={wf_id})")
    else:
        print(f"  Workflow: SKIP (may exist)")

print("\n=== 12. 创建评估数据集 ===")
eval_path = os.path.join(base, 'data', 'eval-dataset.yaml')
if os.path.exists(eval_path):
    try:
        r = upload_file('/api/v1/evaluations/datasets/import', eval_path, token, {'name': '运维巡检评估', 'agentType': 'ops-inspector'})
        print(f"  Dataset: {'OK' if r and r.get('code') == 200 else 'SKIP'}")
    except:
        print("  Dataset: SKIP")

print("\n=== 13. 创建 A/B 测试 ===")
ab_data = {
    'name': '巡检报告格式对比',
    'agentId': agent_id,
    'trafficPercent': 100,
    'variants': [
        {'name': '简洁版', 'weight': 50, 'promptName': 'ops-inspector-concise'},
        {'name': '详细版', 'weight': 50, 'promptName': 'ops-inspector-detailed'}
    ]
}
r = api('POST', '/api/v1/ab-tests', ab_data, token)
if r and r.get('code') == 200:
    ab_id = r['data'].get('id')
    if ab_id:
        api('PUT', f'/api/v1/ab-tests/{ab_id}/start', token=token)
        print(f"  A/B Test: created + started (id={ab_id})")
else:
    print(f"  A/B Test: SKIP (may exist)")

print(f"\n✅ 演示数据灌入完成！")
print(f"   Agent: {agent_id} (巡检) / {agent2_id} (报告) / {agent3_id} (告警)")
print(f"   KB: {kb_id}")
print(f"   TOKEN: {token}")
