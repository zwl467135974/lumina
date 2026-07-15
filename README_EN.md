# Lumina Framework

<div align="center">

**Lumina AI Agent Platform Framework**

A next-generation AI Agent development platform built on AgentScope and Spring Cloud

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen)](https://spring.io/projects/spring-boot)
[![AgentScope](https://img.shields.io/badge/AgentScope-1.0.7-blue)](https://github.com/modelscope/agentscope-java)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

[![CI](https://github.com/zwl467135974/lumina/actions/workflows/ci.yml/badge.svg)](https://github.com/zwl467135974/lumina/actions/workflows/ci.yml)

English | [中文](README.md)

</div>

---

## Introduction

Lumina is an enterprise-grade AI Agent platform built on [AgentScope Java](https://github.com/modelscope/agentscope-java) and [Spring Cloud](https://spring.io/projects/spring-cloud). It provides out-of-the-box Agent capabilities, microservice architecture, and enterprise features.

### Core Features

- **AgentScope Integration** - Native ReAct Agent with tool calling and streaming output
- **MCP Protocol** - Model Context Protocol support via stdio/http transport, auto-registering external MCP server tools (McpClientRegistry + McpToolRegistrar) with read-only monitoring API
- **Tool System** - Unified registration for general tools (HTTP/time/search/calc) + business tools + MCP tools, with circuit breaker, invocation recording, and timeout control
- **Web Search** - Strategy pattern adapting Zhipu/Tavily/SerpAPI/Brave search engines, config-driven switching
- **Workflow Orchestration** - DAG-based workflow engine with 6 node types (Agent / Condition / Loop / Parallel / Transform / Human) + 5 collaboration templates
- **Prompt Management** - Versioned prompt templates with DB-backed activation and runtime resolution
- **Agent Evaluation** - YAML datasets + 4 scorers (exact match / contains / semantic similarity / LLM judge) + A/B comparison + CSV export
- **RAG Knowledge Base** - Document upload → chunking → vectorization → retrieval-augmented generation (Qdrant + multi-provider embeddings) + source visualization
- **Notification Center** - Standalone notification module with in-app notifications, read/unread management
- **Multimodal** - Image/PDF/Word upload + streaming multimodal execution (LumUploader reusable component)
- **Microservice Architecture** - Spring Cloud Alibaba with service discovery, config management, and load balancing
- **Simplified Layered Architecture** - Clear separation: API, Service, Domain, Infrastructure
- **Multi-turn Dialog & Memory** - Session-scoped context persistence (Redis hot memory + DB cold storage), history replay, token usage tracking
- **Async Task Execution** - Submit and return immediately, background thread pool execution, dedicated task list page
- **Full Observability** - MDC structured logging + audit trail + Micrometer metrics (Prometheus/Grafana) + OpenTelemetry tracing (Jaeger)
- **Multi-LLM Support** - DashScope, OpenAI/DeepSeek, Claude, Ollama
- **Security** - Prompt injection detection, output PII sanitization, rate limiting (Redis sliding window), content moderation, multi-tenant RBAC
- **Cost Management** - Token-based cost calculation with model pricing, trend charts, and dashboards
- **Engineering** - Unified error codes, Flyway migrations (V1-V26), gateway rate limiting, tool circuit breaker
- **Frontend** - Dynamic menus, Agent debug panel, dark theme, i18n

---

## Project Structure

### Backend Modules

```
lumina/
├── lumina-common/              # Common utilities, unified response, exception hierarchy
├── lumina-framework/           # Framework infrastructure, global exception handling, web config
├── lumina-agent-core/          # Agent execution engine, config loading, tool management, MCP integration
├── lumina-gateway/             # API gateway, routing, JWT auth, rate limiting
└── lumina-modules/             # Business module aggregator
    ├── lumina-business-base/       # User, role, permission, tenant management (multi-tenant RBAC)
    ├── lumina-business-agent/      # Agent CRUD, conversations, knowledge base, workflows, prompts, MCP monitoring
    └── lumina-business-notification/ # Notification center (in-app notifications)
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
│   └── views/                  # Page components
└── package.json
```

### Module Dependencies

| Module | Description | Depends On |
|--------|-------------|------------|
| **lumina-common** | Unified response, exceptions, utilities, context | - |
| **lumina-framework** | Config classes, exception handler, audit, storage | lumina-common |
| **lumina-agent-core** | AgentScope integration, ReAct engine, memory, tools, RAG, workflow | lumina-common |
| **lumina-gateway** | API gateway, JWT, rate limiting, dynamic routing | lumina-common, lumina-framework |
| **lumina-business-base** | User/role/permission/tenant CRUD, Flyway migrations | lumina-common, lumina-framework, lumina-agent-core |
| **lumina-business-agent** | Agent management, conversations, knowledge, workflows, prompts, cost | lumina-common, lumina-agent-core, lumina-framework |

---

## Quick Start

### Prerequisites

#### Backend

- **JDK 21+** - [Download](https://adoptium.net/)
- **Maven 3.9+** - [Download](https://maven.apache.org/download.cgi)
- **MySQL 8.0+** - [Download](https://dev.mysql.com/downloads/mysql/)
- **Redis 7.0+** - [Download](https://redis.io/download)

#### Frontend

- **Node.js 20+** - [Download](https://nodejs.org/)
- **pnpm** - [Download](https://pnpm.io/)

### Installation

#### 1. Clone

```bash
git clone https://github.com/zwl467135974/lumina.git
cd lumina
```

#### 2. Start Infrastructure

```bash
# MySQL
docker run -d --name mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 \
  -e MYSQL_DATABASE=lumina_dev \
  mysql:8.0

# Redis
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

#### 3. Build & Run Backend

```bash
# Create database (Flyway auto-migrates on startup)
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS lumina_dev"

# Build
mvn clean install -DskipTests

# Start business-base (port 8082)
java -jar lumina-modules/lumina-business-base/target/lumina-business-base-1.0.0-SNAPSHOT.jar

# Start business-agent (port 8081)
java -jar lumina-modules/lumina-business-agent/target/lumina-business-agent-1.0.0-SNAPSHOT.jar

# Start gateway (port 8080)
java -jar lumina-gateway/target/lumina-gateway-1.0.0-SNAPSHOT.jar
```

#### 4. Build & Run Frontend

```bash
cd lumina-frontend
pnpm install
pnpm dev
```

Open http://localhost:3000

---

## Key APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/agents` | Create Agent |
| POST | `/api/v1/agents/{id}/execute` | Execute Agent (sync) |
| POST | `/api/v1/agents/{id}/execute/stream` | Execute Agent (SSE streaming) |
| POST | `/api/v1/agents/{id}/execute/multimodal` | Execute with images |
| POST | `/api/v1/agents/{id}/execute/async` | Submit async task |
| GET | `/api/v1/agents/tasks` | List async tasks |
| GET | `/api/v1/agents/tasks/{uuid}` | Query async task status |
| GET | `/api/v1/workflows/templates` | Workflow templates |
| POST | `/api/v1/workflows/{id}/execute` | Execute workflow |
| GET | `/api/v1/prompts/{name}/active` | Get active prompt |
| GET | `/api/v1/cost/summary` | Cost summary |
| GET | `/api/v1/cost/trend` | Cost trend |
| POST | `/api/v1/evaluations/datasets` | Create evaluation dataset |
| POST | `/api/v1/evaluations/datasets/{id}/runs` | Run evaluation |
| GET | `/api/v1/evaluations/datasets/{id}/trend` | Evaluation trend |
| GET | `/api/v1/evaluations/runs/compare` | A/B compare two runs |
| GET | `/api/v1/evaluations/runs/{id}/export` | Export CSV |
| GET | `/api/v1/mcp/servers` | MCP server status |
| GET | `/api/v1/mcp/tools` | List MCP tools |
| GET | `/api/v1/notifications` | List notifications |

---

## Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5, Spring Cloud 2023.0.3 |
| AI Engine | AgentScope 1.0.7 |
| Database | MySQL 8.0, Flyway V1-V26 |
| ORM | MyBatis-Plus 3.5.7 |
| Cache | Redis 7 + Redisson 3.24.3 |
| Search | Qdrant (RAG vector store) |
| Frontend | Vue 3, TypeScript, Element Plus, Pinia, Vite |
| Observability | Micrometer, OpenTelemetry, Jaeger |
| Resilience | Resilience4j (retry, circuit breaker) |
| Storage | LocalDisk / MinIO |

---

## Deployment

### Docker Compose

```bash
docker-compose up -d
```

### Kubernetes (Helm)

```bash
helm install lumina deploy/helm/lumina \
  --set secrets.jwtSecret="your-secret" \
  --set secrets.llmApiKey="your-key"
```

See [Deployment Guide](docs/zh/deployment/部署指南.md) for details.

---

## License

[Apache License 2.0](LICENSE)
