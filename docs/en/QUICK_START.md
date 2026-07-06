# Quick Start (5 Minutes)

## Prerequisites

- **JDK 21+**
- **MySQL 8.0+** (running on localhost:3306)
- **Redis 7.0+** (running on localhost:6379)
- **Maven 3.9+**
- **Node.js 20+** with **pnpm**

## 1. Database Setup

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS lumina_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
```

Flyway auto-migrates on first startup (V1–V12).

## 2. Set API Key

```bash
# Windows PowerShell
$env:LLM_API_KEY="your-deepseek-api-key"

# Linux/Mac
export LLM_API_KEY="your-deepseek-api-key"
```

## 3. Start Backend Services

```bash
# Start business-base (port 8082, runs Flyway migrations)
mvn spring-boot:run -pl lumina-modules/lumina-business-base

# Start business-agent (port 8081)
mvn spring-boot:run -pl lumina-modules/lumina-business-agent

# Start gateway (port 8080)
mvn spring-boot:run -pl lumina-gateway
```

## 4. Start Frontend

```bash
cd lumina-frontend
pnpm install
pnpm dev
```

Open http://localhost:3000

## 5. Login

- Username: `admin`
- Password: `admin123`
- Tenant: `0` (System)

## 6. Create Your First Agent

1. Navigate to **Agent Management** → **Create**
2. Set name: `My Assistant`, type: `assistant`
3. Open the agent → start chatting in the **Conversation** panel

## Default Ports

| Service | Port |
|---------|------|
| Frontend (dev) | 3000 |
| Gateway | 8080 |
| Business Agent | 8081 |
| Business Base | 8082 |
| MySQL | 3306 |
| Redis | 6379 |

## Docker Compose (Alternative)

```bash
docker-compose up -d
# All 15 services start (MySQL, Redis, Nacos, RocketMQ, Qdrant, Jaeger, Prometheus, Grafana, MinIO, + 4 app services)
```

## Next Steps

- [Architecture](ARCHITECTURE.md) — Module design and dependency flow
- [Agent Development](AGENT_DEVELOPMENT.md) — How to build custom agents
- [Workflow Design](WORKFLOW_DESIGN.md) — Multi-agent orchestration
- [Deployment (Chinese)](../zh/deployment/部署指南.md) — Docker Compose + K8s Helm
