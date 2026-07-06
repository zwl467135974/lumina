# Workflow Design Guide

## Overview

Lumina's workflow engine enables multi-agent collaboration through DAG (Directed Acyclic Graph) orchestration. Define workflows in YAML or use the visual designer.

## Node Types

| Type | Description | Key Properties |
|------|-------------|----------------|
| `agent` | Execute an Agent | `agentId`, `input` (SpEL), `outputVar` |
| `condition` | Branch on expression | `expression`, `trueBranch`, `falseBranch` |
| `loop` | Iterate over collection | `iterations`, `input` |
| `parallel` | Fan-out concurrent branches | `branches[]`, `waitAll` |
| `transform` | Data transformation | `transformExpr` or `template` |
| `human` | Pause for approval | `decisionVar`, `prompt`, `options` |

## YAML Definition

```yaml
name: "customer-complaint"
description: "Customer complaint handling pipeline"

inputs:
  - name: complaint
    type: string
    required: true

nodes:
  - id: classify
    type: agent
    name: "Classify Intent"
    agentId: 1
    input: "#complaint"
    outputVar: category

  - id: route
    type: condition
    expression: "#category.contains('refund')"
    trueBranch: refund-agent
    falseBranch: general-agent

  - id: refund-agent
    type: agent
    name: "Refund Handler"
    agentId: 2
    input: "#complaint"
    outputVar: result

  - id: general-agent
    type: agent
    name: "General Handler"
    agentId: 3
    input: "#complaint"
    outputVar: result

edges:
  - from: classify
    to: route
  - from: refund-agent
    to: done
  - from: general-agent
    to: done

outputs:
  result: "#result"
```

## Collaboration Patterns

### 1. Pipeline (Sequential)
Agent A → Agent B → Agent C. Each agent's output feeds into the next.

### 2. Supervisor-Worker
Supervisor decomposes task → Workers execute in parallel → Supervisor aggregates.

### 3. Router
Classifier agent determines intent → Routes to specialized agent.

### 4. Debate
Proponent argues → Opponent rebuts → Judge synthesizes verdict.

### 5. Human-in-the-Loop
Agent prepares → Human approves/rejects → Agent executes or stops.

## SpEL Expressions

Inputs use Spring Expression Language:

| Expression | Meaning |
|-----------|---------|
| `#variable` | Reference context variable |
| `'#input + " suffix"'` | String concatenation |
| `#result.toUpperCase()` | Method call |
| `#category == 'refund'` | Condition |
| `${name}` | Template variable (Transform node) |

## Visual Designer

Access at `/workflow/designer`:
1. **Drag nodes** from the left palette onto the canvas
2. **Connect nodes** by dragging from bottom handle to top handle
3. **Edit properties** in the right panel (click a node to select)
4. **Switch to YAML** mode for direct text editing
5. **Save** creates/updates the workflow definition

## Execution

### Synchronous
```bash
POST /api/v1/workflows/{id}/execute
Body: {"inputs": {"complaint": "I want a refund"}}
```

### Streaming (SSE)
```bash
POST /api/v1/workflows/{id}/execute/stream
# Returns: NODE_STARTED → NODE_COMPLETED → ... → WORKFLOW_COMPLETED
```

### Resume (Human-in-the-Loop)
```bash
POST /api/v1/workflows/instances/{instanceId}/resume?decision=approved
```

## Observability

- **SSE Progress**: Real-time node status via streaming
- **Multi-Agent Bubbles**: Each agent's result shown in conversation view
- **Execution Logs**: Per-node input/output/duration persisted in DB
- **Micrometer Metrics**: `workflow.execution.duration`, `workflow.node.duration`
- **Budget Control**: Workflow execution respects budget rules
