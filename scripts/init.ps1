# Lumina 一键启动脚本（Windows PowerShell）
# 用法: .\scripts\init.ps1

$ErrorActionPreference = "Stop"
$ProjectDir = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectDir

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Lumina AI Agent Platform - 初始化部署" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# ---------- 1. 检查依赖 ----------
Write-Host ""
Write-Host "[1/6] 检查依赖..."

function Check-Cmd($name, $cmd) {
    if (Get-Command $cmd -ErrorAction SilentlyContinue) {
        $ver = & $cmd --version 2>$null | Select-Object -First 1
        Write-Host "  [OK] $name" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] 未安装 $name ($cmd)，请先安装" -ForegroundColor Red
        exit 1
    }
}

Check-Cmd "Docker" "docker"
Check-Cmd "Java" "java"
Check-Cmd "Maven" "mvn"

# ---------- 2. 环境变量 ----------
Write-Host ""
Write-Host "[2/6] 配置环境变量..."

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "  [WARN] 已从模板生成 .env，请编辑填入真实密钥后重新运行" -ForegroundColor Yellow
    Write-Host "  必须修改: MYSQL_ROOT_PASSWORD / LUMINA_JWT_SECRET / LLM_API_KEY / RAG_EMBEDDING_API_KEY" -ForegroundColor Yellow
    exit 0
} else {
    Write-Host "  [OK] .env 已存在" -ForegroundColor Green
}

# ---------- 3. 构建后端 ----------
Write-Host ""
Write-Host "[3/6] 构建后端（Maven）..."
& mvn install -DskipTests -q
if ($LASTEXITCODE -ne 0) { Write-Host "  构建失败" -ForegroundColor Red; exit 1 }
Write-Host "  [OK] 后端构建完成" -ForegroundColor Green

# ---------- 4. 构建前端 ----------
Write-Host ""
Write-Host "[4/6] 构建前端（pnpm）..."
Push-Location lumina-frontend
if (-not (Test-Path "node_modules")) { & pnpm install }
& pnpm build
if ($LASTEXITCODE -ne 0) { Write-Host "  前端构建失败" -ForegroundColor Red; exit 1 }
Pop-Location
Write-Host "  [OK] 前端构建完成" -ForegroundColor Green

# ---------- 5. 启动 Docker 服务 ----------
Write-Host ""
Write-Host "[5/6] 启动 Docker Compose..."
& docker compose up -d --build
Write-Host "  [OK] 服务启动中" -ForegroundColor Green

# ---------- 6. 健康检查 ----------
Write-Host ""
Write-Host "[6/6] 等待服务就绪..."
Start-Sleep -Seconds 15

function Check-Health($name, $url) {
    try {
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        Write-Host "  [OK] $name" -ForegroundColor Green
    } catch {
        Write-Host "  [WAIT] $name（启动中，请稍后检查）" -ForegroundColor Yellow
    }
}

Check-Health "网关" "http://localhost:8080/actuator/health"
Check-Health "Jaeger UI" "http://localhost:16686"
Check-Health "Qdrant" "http://localhost:6333/healthz"

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  部署完成！" -ForegroundColor Green
Write-Host "  前端:    http://localhost"
Write-Host "  API:     http://localhost:8080"
Write-Host "  追踪:    http://localhost:16686"
Write-Host "  Grafana: http://localhost:3001"
Write-Host "==========================================" -ForegroundColor Cyan
