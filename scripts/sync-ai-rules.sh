#!/usr/bin/env bash
# sync-ai-rules.sh — Sync AGENTS.md to all AI tool config files
#
# Source:  AGENTS.md (used by opencode)
# Targets: .cursorrules (Cursor)
#          CLAUDE.md (Claude Code)
#          .github/copilot-instructions.md (GitHub Copilot)
#
# Usage: bash scripts/sync-ai-rules.sh

set -e

SOURCE="AGENTS.md"
TARGETS=(".cursorrules" "CLAUDE.md" ".github/copilot-instructions.md")

if [ ! -f "$SOURCE" ]; then
  echo "ERROR: Source file $SOURCE not found"
  exit 1
fi

echo "Source: $SOURCE ($(wc -l < "$SOURCE") lines)"

for target in "${TARGETS[@]}"; do
  mkdir -p "$(dirname "$target")"
  cp "$SOURCE" "$target"
  echo "Synced: $target ($(wc -l < "$target") lines)"
done

echo "Done. All AI tool config files updated."
echo "Edit AGENTS.md, then re-run this script."
