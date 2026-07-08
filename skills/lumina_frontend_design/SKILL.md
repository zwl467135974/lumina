---
name: lumina_frontend_design
description: Use this skill when designing, building, or modifying frontend UI — Vue 3 SFC, Element Plus components, CSS/SCSS, layouts, themes, or animations. Enforces Luminous design system consistency and accumulates design learnings across sessions.
---

# Lumina Frontend Design Skill

## When to Apply

**Must Use** (always load this skill):
- Creating or modifying `.vue` files (template, style, or script affecting UI)
- Writing or editing SCSS/CSS in `src/assets/styles/`
- Building new pages, components, or layouts
- Theme changes (dark/light, color adjustments)

**Recommended**:
- Adding ECharts/visualization configurations
- Modifying i18n keys that affect UI labels
- Responsive design adjustments

**Skip**:
- Pure backend Java changes
- API type definitions with no visual impact
- Test file modifications
- Build config / package.json changes

## Design Philosophy: "Luminous"

Lumina's UI follows the **Luminous** design language:
- **Brand**: Indigo-Violet `#7c3aed` × Amber `#f59e0b` — confident, warm, techy
- **Mood**: Dark-first, deep space backgrounds with glowing accents
- **Density**: Comfortable spacing, breathable cards, clear visual hierarchy
- **Motion**: Subtle transitions (150-400ms), staggered entrance animations, no bounce
- **Typography**: Outfit (display) / IBM Plex Sans (body) / JetBrains Mono (code)

## Self-Evolution Protocol (CRITICAL)

This skill **learns from every session**. Follow exactly:

### Before any UI task:
1. Read `lumina-frontend/DESIGN.md` — this is the current design source of truth
2. Read `lumina-frontend/.agents/ui-learnings.md` — accumulated discoveries
3. Summarize 3-5 relevant learnings before proceeding (do not skip this step)

### After completing a UI task:
1. Update `DESIGN.md` if design tokens, patterns, or principles changed
2. Append to `.agents/ui-learnings.md`:
   ```
   ## [Date] — [Task type]
   - Observation: [what happened / what worked / what didn't]
   - Action: [what to do or avoid going forward]
   - Confidence: [high/medium/low]
   ```
3. Reject vague entries ("be careful with styling" = useless). Be specific.
4. Prune Learnings.md at ~80 lines — signal density over size.

## Workflow (4 Phases)

### Phase 1: Audit (read-only)
- Read `lumina-frontend/src/assets/styles/variables.scss` — the `--lumina-*` token system
- Read `lumina-frontend/DESIGN.md` — current design state
- Scan target files for hardcoded colors, non-token spacing, missing CSS vars
- Report drift (e.g., `#fff` instead of `var(--lumina-bg-card)`)

### Phase 2: Plan
- Identify page type: dashboard / form / list-table / chat / designer / dialog
- Check if `DESIGN.md` has page-specific overrides
- Propose component approach using Element Plus + `--lumina-*` tokens
- Do NOT modify `variables.scss` without explicit confirmation

### Phase 3: Confirm
Ask the user (pause before coding):
1. Page flavor (e.g., dense data table vs. spacious marketing card)
2. Any motion-sensitive context (accessibility)
3. Whether to update DESIGN.md with new patterns

### Phase 4: Build
- Use `--lumina-*` CSS variables — NEVER hardcode hex colors
- Use Element Plus components with `--el-*` mapping (already remapped in `index.scss`)
- Scoped styles in `<style scoped lang="scss">`
- Responsive: `@media (max-width: 768px)` breakpoints
- Run `pnpm build` to verify TypeScript + Vite compilation

## Rule Categories (Priority Order)

| Priority | Category | Key Checks | Anti-Patterns |
|----------|----------|------------|---------------|
| P0 | `color-tokens` | All colors use `var(--lumina-*)` | Hardcoded `#fff`, `#333`, `rgba()` |
| P0 | `dark-theme` | Dark-first design, WCAG AA contrast | Light-only testing, unreadable text |
| P0 | `spacing-scale` | Use `--lumina-spacing-*` steps | Random `margin: 13px` |
| P1 | `typography` | Outfit/IBM Plex/JetBrains Mono stack | Arial, system-ui as primary |
| P1 | `component-consistency` | Same component type looks same everywhere | Ad-hoc card styles per page |
| P1 | `motion` | 150-400ms, cubic-bezier(0.4,0,0.2,1) | `transition: all 0.5s` |
| P2 | `responsive` | el-row with xs/sm/md breakpoints | Fixed widths on mobile |
| P2 | `accessibility` | focus-visible, aria-labels, contrast ≥ 4.5:1 | Removing focus outlines |
| P2 | `empty-states` | el-empty with description and action | Blank screen when no data |

## Quick Reference: Token Lookups

| Need | Token | Value |
|------|-------|-------|
| Primary color | `var(--lumina-primary)` | #7c3aed |
| Accent color | `var(--lumina-accent)` | #f59e0b |
| Card background | `var(--lumina-bg-card)` | #1e293b |
| Page background | `var(--lumina-bg-page)` | #0f172a |
| Primary text | `var(--lumina-text-primary)` | #f1f5f9 |
| Secondary text | `var(--lumina-text-secondary)` | #94a3b8 |
| Border | `var(--lumina-border)` | #334155 |
| Card radius | `var(--lumina-radius-md)` | 10px |
| Base spacing | `var(--lumina-spacing-md)` | 16px |
| Fast transition | `var(--lumina-transition-fast)` | 150ms |
| Display font | `var(--lumina-font-display)` | Outfit |
| Body font | `var(--lumina-font-body)` | IBM Plex Sans |
| Code font | `var(--lumina-font-mono)` | JetBrains Mono |
| Card shadow | `var(--lumina-shadow-md)` | 0 4px 12px rgba(0,0,0,0.4) |
| Glow effect | `var(--lumina-shadow-glow)` | 0 0 20px primary-glow |

## References

- **Design tokens**: `lumina-frontend/src/assets/styles/variables.scss` (13 sections, ~130 vars)
- **Global styles**: `lumina-frontend/src/assets/styles/index.scss` (Element Plus overrides)
- **Design state**: `lumina-frontend/DESIGN.md` (persistent source of truth)
- **Learnings**: `lumina-frontend/.agents/ui-learnings.md` (accumulated discoveries)
- **Component examples**: `lumina-frontend/src/views/dashboard/index.vue` (stat cards), `lumina-frontend/src/views/login/index.vue` (glass morphism), `lumina-frontend/src/layouts/components/AppSidebar.vue` (dark sidebar)

## Pre-Delivery Checklist

Before marking any UI task complete:
- [ ] No hardcoded hex colors (grep for `#[0-9a-f]` in `.vue` files)
- [ ] All spacing uses `--lumina-spacing-*` or 8px grid multiples
- [ ] Dark theme verified (text readable on `--lumina-bg-base`)
- [ ] `pnpm build` passes (vue-tsc + Vite)
- [ ] Empty/loading/error states handled
- [ ] Responsive at 768px breakpoint
- [ ] Learnings.md updated if new pattern discovered
