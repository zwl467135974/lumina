# sync-ai-rules.ps1 - Sync AGENTS.md to all AI tool config files
#
# Source:  AGENTS.md (used by opencode)
# Targets: .cursorrules (Cursor)
#          CLAUDE.md (Claude Code)
#          .github/copilot-instructions.md (GitHub Copilot)
#
# Usage: powershell -ExecutionPolicy Bypass -File scripts/sync-ai-rules.ps1

$ErrorActionPreference = "Stop"

$source = "AGENTS.md"
$targets = @(".cursorrules", "CLAUDE.md", ".github/copilot-instructions.md")

if (-not (Test-Path $source)) {
    Write-Host "ERROR: Source file $source not found" -ForegroundColor Red
    exit 1
}

$sourceLines = (Get-Content $source).Count
Write-Host "Source: $source ($sourceLines lines)" -ForegroundColor Cyan

foreach ($target in $targets) {
    $targetDir = Split-Path $target -Parent
    if ($targetDir -and -not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }
    Copy-Item -Path $source -Destination $target -Force
    $lines = (Get-Content $target).Count
    Write-Host "Synced: $target ($lines lines)" -ForegroundColor Green
}

Write-Host "Done. All AI tool config files updated." -ForegroundColor Yellow
Write-Host "Edit AGENTS.md, then re-run this script."
