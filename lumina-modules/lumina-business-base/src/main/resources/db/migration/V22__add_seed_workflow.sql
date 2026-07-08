-- V22: 补充示例工作流种子数据

INSERT INTO lumina_workflow_definition (name, description, definition_yaml, version, status, tenant_id, create_by)
VALUES (
    '客服意图分类路由',
    '示例工作流：意图分类 Agent → 条件路由 → 退款/通用 Agent → 汇总输出',
    'name: "customer-complaint-handler"
description: "客户投诉处理流程"
inputs:
  - name: complaint
    type: string
    required: true
nodes:
  - id: classify
    type: agent
    agentId: 2
    input: "$.complaint"
    output: "category"
  - id: route
    type: condition
    expression: "#category.contains(''refund'')"
    branches:
      - condition: true
        to: refund-agent
      - condition: false
        to: general-agent
  - id: refund-agent
    type: agent
    agentId: 2
    input: "$.complaint"
    output: "refund_result"
  - id: general-agent
    type: agent
    agentId: 1
    input: "$.complaint"
    output: "general_result"
edges:
  - from: classify
    to: route
  - from: refund-agent
    to: end
  - from: general-agent
    to: end
outputs:
  result: "$.refund_result ?: $.general_result"',
    1, 1, 0, 1
)
ON DUPLICATE KEY UPDATE description = VALUES(description);
