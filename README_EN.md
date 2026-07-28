# Lumina Framework

<div align="center">

**Lumina AI Agent Platform Framework**

An enterprise-grade, private-deployment AI Agent platform built on AgentScope and Spring Cloud Alibaba

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen)](https://spring.io/projects/spring-boot)
[![AgentScope](https://img.shields.io/badge/AgentScope-1.0.7-blue)](https://github.com/modelscope/agentscope-java)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

[![CI](https://github.com/zwl467135974/lumina/actions/workflows/ci.yml/badge.svg)](https://github.com/zwl467135974/lumina/actions/workflows/ci.yml)

English | [中文](README.md)

</div>

---

## Introduction

Lumina is an **enterprise private-deployment AI Agent platform** built on [AgentScope Java](https://github.com/modelscope/agentscope-java) and [Spring Cloud Alibaba](https://spring.io/projects/spring-cloud-alibaba). It targets **private deployment + multi-tenancy + cost accountability** — for Chinese ToB vendors and enterprise IT departments who need on-premise, tenant-isolated AI Agent infrastructure.

### Why Lumina

Our net advantage over Dify OSS / LangGraph / Spring AI Alibaba is **enterprise-grade features**:

| Capability | Dify OSS | LangGraph | Spring AI Alibaba | **Lumina** |
|---|---|---|---|---|
| **Row-level multi-tenancy** | ❌ workspace-level | ❌ N/A | ❌ N/A | ✅ fail-closed + integration tested |
| **5-table RBAC + audit** | partial | ❌ | ❌ | ✅ `@Audit` AOP |
| **Budget control (token billing)** | partial | ❌ | ❌ | ✅ per-tenant/agent |
| **Prompt injection detection + PII masking** | partial | ❌ | ❌ | ✅ 11 patterns |
| **JWT fail-fast + header anti-forgery** | N/A | N/A | N/A | ✅ gateway-level stripping |

### 30-Second Quick Start

```bash
export LLM_API_KEY=your-glm-or-dashscope-key
docker compose -f docker-compose-standalone.yml up
# Open http://localhost:8080, admin / admin123
```

Only MySQL + Redis required (compose includes both). No Nacos / RocketMQ / separate Gateway process needed.

### Five Pillars

- **🏢 Enterprise Features** - Row-level multi-tenancy (fail-closed), 5-table RBAC, audit logging, budget control, JWT fail-fast, Prompt injection detection + PII masking — the most battle-tested part of the codebase, with integration tests
- **🤖 Agent Execution Engine** - AgentScope 1.0.7 ReAct/Plan-Execute, SSE streaming (REASONING/ACTING/RAG_SOURCES), multimodal, Provider Failover
- **🔧 Tools & Integration** - MCP protocol (stdio/SSE/streamable-http + header auth + reconnect health check), OpenAI-compatible `/v1/chat/completions` exit, Webhook, WeCom bot, Code Interpreter (Docker pool)
- **📚 Knowledge & Orchestration** - Hybrid RAG retrieval (RRF + reranker + 5 OCR), Flowable 7.0 DAG workflow (6 nodes + 7 templates), Prompt versioning, Agent evaluation regression (4 scorers + A/B comparison)
- **🎨 Engineering Frontend** - Vue 3 + Element Plus 32 views, dark theme, i18n, Agent debug panel, permission-driven dynamic menus

### Architecture

> Two deployment modes share the same business code: **standalone** (single process, MySQL+Redis only, for PoC/trial) and **microservice** (Gateway+Base+Agent three services, for production).

```
Clients (Vue3 / OpenAI SDK)
    │
    ▼
Gateway / Standalone Filter  (JWT verify + identity header anti-forgery)
    │
    ├── Base Service    (Users / Multi-tenant RBAC / Audit / Budget)
    ├── Agent Service   (ReAct + Plan-Execute + RAG + Workflow)
    ├── Notification    (SSE / Webhook / WeCom bot)
    └── Cron Trigger    (scheduled execution + distributed lock)
    │
    ▼
MySQL 8 + Redis 7 + Qdrant (vector search + tenant filter)
    +
LLM (GLM / DashScope / OpenAI / Ollama) / MCP Server / External Webhook
```

---

## Project Structure

### Backend Modules

```
lumina/
├── lumina-common/              # Common utilities, unified response, exception hierarchy
├── lumina-framework/           # Framework infrastructure, global exception handling, web config
├── lumina-agent-core/          # Agent core (execution engine, Flowable workflow, config loading, tool management, MCP, Resilience4j)
├── lumina-gateway/             # API gateway (unified entry, JWT auth, OpenAI-compatible routing)
├── lumina-standalone/          # Standalone launcher (base+agent+notification merged, MySQL+Redis only)
└── lumina-modules/             # Business module aggregator
    ├── lumina-business-base/       # Base business (users, roles, permissions, multi-tenancy, audit, budget, API Token)
    ├── lumina-business-agent/      # Agent business (Agent config, knowledge base, workflow, Cron triggers, evaluation, prompts)
    └── lumina-business-notification/ # Notification center (in-app SSE, Webhook, WeCom)
```

### Frontend

```
lumina-frontend/
├── src/
│   ├── api/                    # API definitions
│   ├── components/             # Shared components
│   ├── composables/            # Vue composables
│   ├── layouts/                # Layout components
│   ├── router/                 # Route configuration
│   ├── stores/                 # State management (Pinia)
│   ├── types/                  # TypeScript types
│   ├── utils/                  # Utilities
│   └── views/                  # Page components (32 views)
└── package.json
```

---

## Quick Start

### Option A: Standalone Mode (Recommended for Trial)

Only needs **MySQL 8 + Redis 7**. No Nacos / RocketMQ / separate Gateway.

```bash
git clone https://github.com/zwl467135974/lumina.git
cd lumina

# Required: LLM API Key
export LLM_API_KEY=your-api-key

# One command brings up MySQL + Redis + Lumina (port 8080)
docker compose -f docker-compose-standalone.yml up
```

After startup:
- Health check: http://localhost:8080/actuator/health
- Default account: `admin` / `admin123` (system tenant, tenant_id=0)

See [Standalone Deployment Guide](docs/zh/deployment/standalone部署.md).

### Option B: Microservice Mode (For Production)

#### Prerequisites

- **JDK 21+** + **Maven 3.9+** + **MySQL 8.0+** + **Redis 7.0+**
- **Nacos 3.1.1+** (**required**: service discovery + config center)
- **Node.js 20+** + **pnpm** (frontend)

#### Steps

```bash
# 1. Clone
git clone https://github.com/zwl467135974/lumina.git
cd lumina

# 2. Start MySQL + Redis + Nacos
mysql -u root -p -e "CREATE DATABASE lumina_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 3. Set API key
export LLM_API_KEY=your_api_key_here

# 4. Start backend services (Flyway auto-migrates V1–V44+ on first launch)
mvn spring-boot:run -pl lumina-modules/lumina-business-base   # port 8082
mvn spring-boot:run -pl lumina-modules/lumina-business-agent  # port 8081
mvn spring-boot:run -pl lumina-gateway                        # port 8080

# 5. Start frontend
cd lumina-frontend && pnpm install && pnpm dev               # port 3000
```

---

## Key APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/agents` | Create Agent |
| POST | `/api/v1/agents/{id}/execute` | Execute Agent (sync) |
| POST | `/api/v1/agents/{id}/execute/stream` | Execute Agent (SSE streaming) |
| POST | `/api/v1/agents/{id}/execute/multimodal` | Execute with images/docs |
| POST | `/api/v1/agents/{id}/execute/async` | Submit async task |
| POST | `/v1/chat/completions` | OpenAI-compatible endpoint |
| GET | `/v1/models` | List models (OpenAI-compatible) |
| POST | `/api/v1/workflows/{id}/execute` | Execute workflow |
| POST | `/api/v1/workflows/from-template` | Create workflow from template |
| POST | `/api/v1/agents/triggers` | Create Cron trigger |
| GET | `/api/v1/knowledge/search` | Hybrid vector + keyword search |
| GET | `/api/v1/cost/summary` | Cost summary |
| GET | `/api/v1/evaluations/runs/compare` | A/B compare two runs |
| GET | `/api/v1/mcp/servers` | MCP server status |
| CRUD | `/api/v1/model-pricing` | Model pricing management |
| Swagger UI | `/swagger-ui.html` | Interactive API docs (SpringDoc OpenAPI) |

---

## Tech Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 3.3.5 |
| Microservices | Spring Cloud Alibaba | 2023.0.1.2 |
| AI Engine | AgentScope Java | 1.0.7 |
| Reactive | Project Reactor | 2025.0.2 |
| ORM | MyBatis-Plus | 3.5.7 |
| Cache | Redisson | 3.24.3 |
| Workflow | Flowable | 7.0.1 |
| Resilience | Resilience4j | 2.2.0 |
| Service Mesh | Nacos | 3.1.1+ |
| API Docs | SpringDoc OpenAPI | 2.6.0 |
| Database Migration | Flyway | V1–V44 |
| JSON | Jackson | 2.20.1 |
| Frontend | Vue 3 + TypeScript + Element Plus + Pinia + Vite |

---

## Deployment

### Standalone (Docker Compose)

```bash
export LLM_API_KEY=your-key
docker compose -f docker-compose-standalone.yml up -d
```

### Microservice (Docker Compose)

```bash
cp .env.example .env  # edit secrets
docker compose up -d
```

### Monitoring (optional overlay)

```bash
docker compose -f docker-compose.yml -f docker-compose-monitoring.yml up -d
# Grafana: http://localhost:3001 (3 pre-provisioned dashboards)
```

### Kubernetes (Helm)

```bash
helm install lumina deploy/helm/lumina \
  --set secrets.jwtSecret="your-secret" \
  --set secrets.llmApiKey="your-key"
```

---

## Project Status

### v3.10 — Full Audit Fixes (Release Quality Hardening)

Based on a four-dimensional systematic audit (CI tech debt / layered architecture / exception handling / new feature quality), fixed 6 release-blocking issues + standardized conventions, no breaking API changes.

- 🔒 **Security Fix** — LongTermMemoryController auth vulnerability (delete/deleteAll missing userId check → full-table deletion risk)
- 🏗 **Architecture Compliance** — 2 Controllers extracted to Service layer + VO, DO no longer crosses API boundary
- 🐛 **Feature Bugs ×3** — Cold-start loads recent (not earliest) messages; model routing uses cheap model for complexity judging; MultiAgent routing strict matching
- ⚡ **Performance** — Cold-start warm-up reduced from 300 Redis round-trips to 3
- 📊 **Observability** — State save / config hot-reload failures now emit monitoring counters; MultiAgent summarization integrated into Trace
- 📐 **Conventions** — Error code semantics fixed (MODEL_NOT_FOUND); Jackson instances unified; dependency injection fully constructor-based; guardrail thresholds configurable

### v3.9 — Production-Grade Refinement

- ✅ **DB Cold-Start Memory Recovery** — Restore from MySQL after Redis expiry + warm-up backfill
- ✅ **Conversation-Level Token Budget** — CONVERSATION scope, per-session spend limit
- ✅ **Tool Error Recovery** — Enhanced error messages, LLM auto-corrects parameters and retries
- ✅ **Auto Conversation Management** — `/chat` endpoint, frontend no longer manages conversationId
- ✅ **Per-KB Chunking Strategy** — Each KB configures its own chunkSize/overlap/splitStrategy
- ✅ **58 Tutorial Articles** — Expanded from 47, with self-test answers, all new features documented

### v3.8 — AI Core Capabilities

- ✅ **Agent Loop Limit** — maxIters safety valve, prevents infinite loops burning tokens
- ✅ **Structured Output** — JSON Mode, constrains LLM to return valid JSON
- ✅ **Context Compression** — LLM rolling summary of old messages, no direct discarding
- ✅ **Multi-Agent Collaboration** — Supervisor pattern, LLM router auto-selects experts
- ✅ **Dynamic Model Routing** — Complexity judgment → auto-switch cheap/powerful model
- ✅ **Output Guardrails** — Keyword blocking + length truncation + repetition detection

### v3.7 — AgentScope 2.0 Upgrade + Trace Observability

- ✅ **AgentScope 2.0.0 Upgrade** — From 1.0.7, model extension package path migration, `.memory()` → `.stateStore()`
- ✅ **RedisAgentStateStore** — Cross-instance memory sharing, AgentState Redis persistence (7-day TTL)
- ✅ **Reasoning Trace System** — LuminaTraceTracer full-chain interception + Reactor Context propagation + frontend visualization + data cleanup
- ✅ **Full Path Coverage** — Sync/streaming/PlanAndExecute/FailoverChain all four execution paths covered

### v3.6 — Enterprise Hardening

- ✅ **Model Pricing Management** — Full CRUD UI + API for model input/output pricing (Flyway V44), cost calculation no longer falls back to hardcoded defaults
- ✅ **Controller Permission Audit** — All 18 Agent-module controllers annotated with `@RequirePermission`, verified by `ControllerPermissionTest`
- ✅ **Workflow PAUSED Context Fix** — Human-approval nodes now persist `instance.output` on pause, resume correctly reads context variables
- ✅ **Token Tracking Fix** — Sync/multimodal/streaming execution paths all persist token usage to `agent_task` table; cost dashboard shows real data
- ✅ **API Documentation** — SpringDoc OpenAPI (Swagger UI) with `@Tag`/`@Operation` on all controllers; JWT security scheme configured
- ✅ **Budget In-Flight Tracking** — Budget check counts RUNNING tasks, not just COMPLETED; Redis-based alert deduplication
- ✅ **MCP Runtime Registration** — `registerServer()` now auto-fetches and registers tools to `EnhancedToolManager`

### v3.5 — Automation & Observability

- ✅ **Cron Triggers** — Agent scheduled execution, Redisson distributed lock, misfire policy
- ✅ **Grafana Dashboards** — 3 pre-provisioned dashboards (Agent execution / Tools+RAG / Workflow+Trigger)
- ✅ **Monitoring Overlay** — `docker-compose-monitoring.yml` one-command add-on

### v3.4 — Enterprise Integration

- ✅ **Standalone Mode** — base+agent+notification merged into single jar, MySQL+Redis only
- ✅ **OpenAI-Compatible Exit** — `/v1/chat/completions` + `/v1/models`, standard OpenAI SDK works directly
- ✅ **Vector-layer Tenant Isolation Fix** — Qdrant payload filter pushdown + tenant_id index
- ✅ **MCP Production** — streamable-http + header auth + reconnect health check + runtime registration
- ✅ **Webhook System** — per-user/per-category subscriptions + HMAC-SHA256 signature
- ✅ **WeCom Bot** — markdown rendering + 4096-byte chunking + rate limiting

### Earlier Versions

- **v3.3** — Multimodal PDF/Word, Plan-Execute agent, RAG hybrid retrieval + reranker, evaluation regression
- **v3.2** — MCP protocol, notification center, general tool system, Qdrant integration tests
- **v3.1** — TenantLineHandler auto-detect, Resilience4j circuit breaker, Flowable 7.0 workflow engine, 9 security fixes
- **v3.0** — Stabilization: data layer alignment, full i18n, dark theme, dashboard, permission seeds
- **v2.0** — DAG workflow engine, Prompt management, async tasks, cost management, evaluation framework, K8s Helm

### Test Baseline

- Backend: **787 tests** (unit + integration, all modules `mvn verify` pass)
- Frontend: 103 tests (Vitest)
- CI/CD: GitHub Actions dual pipeline (backend mvn verify + frontend pnpm build + test)

---

## License

[Apache License 2.0](LICENSE)
