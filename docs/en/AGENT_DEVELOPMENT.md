# Agent Development Guide

## Overview

Lumina agents are built on AgentScope's ReAct pattern. Each agent can use tools, access RAG knowledge bases, and maintain multi-turn conversation memory.

## Creating an Agent

### Via UI
1. Navigate to **Agent Management** → **Create**
2. Fill in name, type (e.g., `assistant`, `react`, `customer-service`)
3. Enable the agent (status = active)

### Via API
```bash
curl -X POST http://localhost:8080/api/v1/agents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"agentName": "my-agent", "agentType": "assistant"}'
```

## Agent Types

The `agentType` field determines which prompt template is used:

| Type | Prompt Source | Use Case |
|------|--------------|----------|
| `assistant` | `prompts/assistant.txt` or DB active prompt | General Q&A |
| `react` | `prompts/react.txt` | Tool-calling reasoning |
| Custom | `prompts/{type}.txt` | Domain-specific |

## Prompt Management

Prompts are versioned in the database. The runtime resolution order:

1. **DB Active Prompt**: Search by `agentType.toLowerCase()` → tenant-specific → global fallback
2. **Classpath Fallback**: `prompts/{type}.txt` in `agent-core/src/main/resources/`

To customize a prompt:
1. Go to **Prompt Management** → **Create**
2. Set name matching the agent type (e.g., `assistant`)
3. Write your system prompt with `{0}` placeholder for user input
4. Publish and activate

## Executing an Agent

### Synchronous
```bash
POST /api/v1/agents/{id}/execute?task=Hello
```

### Streaming (SSE)
```bash
POST /api/v1/agents/{id}/execute/stream?task=Hello
# Returns SSE chunks: REASONING_CHUNK → ACTING_CHUNK → FINAL
```

### Async Task
```bash
POST /api/v1/agents/{id}/execute/async
# Returns taskUuid immediately, execute in background
GET /api/v1/agents/tasks/{uuid}/stream  # SSE progress
```

### Multimodal (Image + Text)
```bash
POST /api/v1/agents/{id}/execute/multimodal/stream
# Body: {"task": "Describe this image", "fileUuids": ["uuid-1"]}
```

## Security Pipeline

All execution goes through:

1. **Rate Limiting** — Redis token bucket (30 req/min per user+agent, configurable)
2. **Budget Check** — Reject if tenant/agent/user budget exceeded
3. **Content Moderation** — Rule-based harmful content detection
4. **Prompt Injection Filter** — 11 injection patterns + 4 high-risk tokens
5. **Execution** — AgentScope ReAct with tools/RAG
6. **Output Sanitization** — PII masking (phone, ID card, bank card, email)

## Tool Development

Create a tool by implementing the tool interface:

```java
@Component
public class WeatherTool implements AgentTool {
    @ToolMethod(description = "Get weather for a city")
    public String getWeather(@ToolParam(description = "City name") String city) {
        return "Sunny, 25°C";
    }
}
```

Tools are auto-registered via `EnhancedToolManager`. Tool calls are observable in the debug panel.

## RAG Integration

1. Upload documents via **Knowledge Base** → **Upload**
2. Documents are chunked, embedded (DashScope/OpenAI/Ollama), and stored in Qdrant
3. Agent retrieves relevant chunks automatically (GENERIC mode) or on demand (AGENTIC mode)
4. Retrieved sources are shown in the chat as **Reference Sources**

## Model Configuration

Models are configured via environment variables:

```bash
LLM_TYPE=dashscope        # dashscope / openai / deepseek / anthropic / ollama
LLM_API_KEY=your-key
LLM_MODEL=qwen-plus       # model name
LLM_BASE_URL=             # for OpenAI-compatible APIs
LLM_TEMPERATURE=0.7
LLM_MAX_TOKENS=2000
```

Resilience4j wraps all LLM calls with retry (3 attempts) and circuit breaker (50% failure rate threshold).
