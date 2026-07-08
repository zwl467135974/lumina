# UI Learnings — Accumulated Discoveries

> This file is read before every UI task and updated after.
> Keep entries specific and actionable. Prune at ~80 lines.

## 2026-07-08 — Initial Setup
- Observation: Luminous theme uses 130+ CSS variables in `--lumina-*` namespace, mapped to Element Plus `--el-*` vars in `index.scss`
- Action: Always use `--lumina-*` tokens, never hardcode hex. EP components auto-inherit via remapping.
- Confidence: high

## 2026-07-08 — BOM Encoding Issue
- Observation: PowerShell `Set-Content` corrupts Chinese characters in `.vue` files, causing vue-tsc parse failures ("Could not find declaration file for module")
- Action: Use `[System.IO.File]::WriteAllBytes` with UTF8 encoding for bulk file edits, or use the Edit tool instead of PowerShell
- Confidence: high

## 2026-07-08 — vue-i18n in Tests
- Observation: Components using `useI18n()` fail in tests with "Need to install with `app.use` function" if vue-i18n isn't mocked
- Action: Add `vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))` to test files
- Confidence: high

## 2026-07-08 — Dashboard API Aggregation
- Observation: Dashboard page needed `getStats()` and `getRecentTasks()` but no backend endpoint existed
- Action: Created `src/api/dashboard.ts` that aggregates `listAgents` + `listAgentTasks` + `getCostSummary` via `Promise.allSettled`
- Confidence: high

## 2026-07-08 — Store Import Paths
- Observation: Dark theme redesign reintroduced `@/stores/app` import paths that don't resolve
- Action: Always use `@/stores` barrel export, not individual store files
- Confidence: high
