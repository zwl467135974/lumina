# Lumina Architecture

## Overview

Lumina is an enterprise-grade AI Agent platform built on AgentScope Java and Spring Cloud Alibaba. It provides a simplified layered architecture with clear separation between API, Service, Domain, and Infrastructure layers.

## Module Structure

```
lumina/
├── lumina-common/           # Shared utilities, unified response, exceptions, context
├── lumina-framework/        # Configuration, exception handling, audit, storage, Redis
├── lumina-agent-core/       # AgentScope integration, ReAct engine, workflow, RAG, tools
├── lumina-gateway/          # API gateway, JWT auth, rate limiting, dynamic routing
└── lumina-modules/
    ├── lumina-business-base/   # User/role/permission/tenant management (multi-tenant RBAC)
    └── lumina-business-agent/  # Agent CRUD, conversations, workflows, prompts, cost, budget
```

## Dependency Flow

```
lumina-common (no deps)
    ↑
lumina-framework (depends on common)
    ↑
lumina-agent-core (depends on common + framework)
    ↑
lumina-business-agent (depends on common + agent-core + framework)
lumina-business-base (depends on common + framework)
lumina-gateway (depends on common + framework)
```

Strict unidirectional dependencies — no circular references.

## Key Design Patterns

### 1. AutoConfiguration
8 AutoConfiguration classes in `lumina-framework` provide zero-config bootstrap for business modules.

### 2. ReAct Agent Pipeline
```
User Input → Memory Load → RAG Retrieval (optional) → LLM Call → Tool Execution → Memory Save → Response
```

### 3. Workflow Engine (DAG)
6 node types: Agent / Condition / Loop / Parallel / Transform / Human
5 collaboration templates: Supervisor-Worker / Pipeline / Router / Debate / Human-in-the-Loop

### 4. Security Pipeline
```
Rate Limit (Redis) → Budget Check → Content Moderation → Prompt Injection Filter → Execute → Output PII Sanitization
```

### 5. Streaming (SSE)
- Agent execution: `POST /agents/{id}/execute/stream` → `Flux<StreamChunk>`
- Workflow execution: `POST /workflows/{id}/execute/stream` → `Flux<Event>`
- Async task progress: `GET /agents/tasks/{uuid}/stream` → `Flux<Progress>`

## Technology Stack

| Category | Technology |
|----------|-----------|
| Runtime | Java 21 (LTS) |
| Framework | Spring Boot 3.3.5, Spring Cloud 2023.0.3 |
| AI Engine | AgentScope Java 1.0.7 |
| Database | MySQL 8.0, Flyway V1-V12 |
| ORM | MyBatis-Plus 3.5.7 |
| Cache | Redis 7 + Redisson 3.24.3 |
| Vector Store | Qdrant (RAG) |
| Message Queue | RocketMQ 5.3.1 (optional, with thread pool fallback) |
| Frontend | Vue 3, TypeScript, Element Plus, Pinia, Vite |
| Observability | Micrometer, OpenTelemetry, Jaeger |
| Resilience | Resilience4j (retry, circuit breaker) |
| Workflow Designer | Vue Flow (visual canvas + YAML sync) |
