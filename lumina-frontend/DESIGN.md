# Lumina Design State — "Luminous" Theme

> This file is the persistent source of truth for Lumina's UI design.
> The frontend design skill reads this before every UI task.
> Update this file when design tokens, patterns, or principles change.

## Brand Identity

- **Name**: Luminous
- **Primary**: Indigo-Violet `#7c3aed` — confident, creative, premium
- **Accent**: Amber `#f59e0b` — warmth, energy, attention
- **Philosophy**: Dark-first, deep space backgrounds with glowing accents. Like a nebula — dark voids with luminous points of light.

## Color System

### Brand Colors
| Token | Value | Usage |
|-------|-------|-------|
| `--lumina-primary` | `#7c3aed` | Buttons, links, active states, focus |
| `--lumina-primary-light` | `#a78bfa` | Hover states, secondary accents |
| `--lumina-primary-dark` | `#5b21b6` | Pressed states |
| `--lumina-accent` | `#f59e0b` | Highlights, badges, warm accents |
| `--lumina-accent-light` | `#fbbf24` | Accent hover |

### Semantic Colors
| Token | Value | Usage |
|-------|-------|-------|
| `--lumina-success` | `#10b981` | Success messages, completed status |
| `--lumina-warning` | `#f59e0b` | Warnings, pending status |
| `--lumina-danger` | `#ef4444` | Errors, delete actions, failed status |
| `--lumina-info` | `#3b82f6` | Info messages, informational tags |

### Surface Palette (Dark-first)
| Token | Value | Usage |
|-------|-------|-------|
| `--lumina-bg-base` | `#0f172a` | Page background, deepest layer |
| `--lumina-bg-elevated` | `#1e293b` | Cards, panels, elevated surfaces |
| `--lumina-bg-card` | `#1e293b` | Card backgrounds |
| `--lumina-bg-input` | `#0f172a` | Input fields (recessed) |
| `--lumina-bg-hover` | `rgba(255,255,255,0.04)` | Hover overlay |

### Text Hierarchy
| Token | Value | Usage |
|-------|-------|-------|
| `--lumina-text-primary` | `#f1f5f9` | Headlines, primary content |
| `--lumina-text-regular` | `#cbd5e1` | Body text, default |
| `--lumina-text-secondary` | `#94a3b8` | Labels, descriptions |
| `--lumina-text-muted` | `#64748b` | Placeholders, disabled |

## Typography

| Role | Font | Weight |
|------|------|--------|
| Display (titles) | Outfit | 600-700 |
| Body | IBM Plex Sans | 400-500 |
| Code/Mono | JetBrains Mono | 400 |

Font sizes: 12px (xs) → 14px (base) → 18px (lg) → 24px (2xl) → 36px (4xl)

## Spacing Scale

4px (xs) → 8px (sm) → 16px (md) → 24px (lg) → 32px (xl) → 48px (2xl) → 64px (3xl)

All spacing MUST use these steps or 8px grid multiples.

## Motion

| Token | Duration | Usage |
|-------|----------|-------|
| `--lumina-transition-fast` | 150ms | Hover, toggle, small state changes |
| `--lumina-transition-base` | 250ms | Card transitions, dialog open |
| `--lumina-transition-slow` | 400ms | Page transitions, large element animations |

Easing: `cubic-bezier(0.4, 0, 0.2, 1)` for all transitions.

## Shadow System

| Token | Value | Usage |
|-------|-------|-------|
| `--lumina-shadow-sm` | 0 1px 2px rgba(0,0,0,0.3) | Subtle elevation |
| `--lumina-shadow-md` | 0 4px 12px rgba(0,0,0,0.4) | Cards, dropdowns |
| `--lumina-shadow-lg` | 0 8px 24px rgba(0,0,0,0.5) | Modals, overlays |
| `--lumina-shadow-glow` | 0 0 20px primary-glow | Active/featured elements |

## Component Patterns

### Stat Cards (Dashboard)
- Gradient icon circle (48px, brand color light-8 background)
- Staggered entrance animation (0.08s delay per card)
- Clickable cards navigate to detail page
- Glass morphism on hover (slight lift + shadow)

### Sidebar
- **Layout: flex-shrink:0**（方案 A，不用 fixed）
- Dark gradient background (`--lumina-bg-base` → `#1a1040` → `--lumina-bg-base`)
- 3px gradient active indicator bar (primary → accent)
- Collapse to 64px with icon-only display
- Menu items from DB permission table (dynamic)
- Menu area `overflow-y: auto`（菜单多时可滚动）
- Collapsed state: `.el-menu--collapse` 图标居中
- Submenu: `.el-menu--inline` 有独立背景 + 缩进
- Mobile (`@media 768px`): `position: fixed` + `translateX(-100%)` 抽屉式

### Header
- **Layout: sticky + flex-shrink:0**（方案 A，不用 fixed）
- Glass backdrop blur (backdrop-filter: blur(12px))
- Gradient bottom border (1px, primary → transparent)
- Avatar with glow ring on hover
- Language switch as segmented button
- `.header-left` 有 `min-width: 0`（防 flex 溢出）
- Mobile: 隐藏面包屑 + 语言切换

### Login Page
- Radial gradient orbs (purple top-left, amber bottom-right)
- Animated drift (18s ease-in-out infinite)
- Glass morphism card (backdrop-blur + translucent bg)
- Hexagonal SVG logo with gradient stroke

### Tables
- Dark header row (`--lumina-bg-elevated`)
- Alternating row tint via `--lumina-bg-hover` on even rows
- **Action column: 前 2 个高频按钮 + `el-dropdown` 折叠其余**（按钮 ≥ 4 时）
- Status tags use semantic colors with `size="small"`
- `overflow-x: auto` on `.page-content`（横向溢出可滚动，不裁切）

### Layout Architecture (方案 A — Flex)
- `#app { display: flex; height: 100vh; overflow: hidden }`
- `.lumina-layout { display: flex }` — Sidebar + Main 横向 flex
- `.main-container { flex: 1; min-width: 0 }` — **关键：min-width:0 允许收缩**
- `.page-content { overflow-x: auto }` — 横向溢出改滚动
- **不用 fixed + margin-left 补偿**（之前的方案 C 已废弃）
- **不用 z-index 战争**（Sidebar 和 Header 同级 flex 子项）

### Forms
- Labels above inputs (not inline)
- Input background = `--lumina-bg-input` (recessed)
- Focus border = `--lumina-border-focus` (primary color)
- Submit button full-width in dialogs

## Page-Specific Overrides

### Dashboard
- 4-column stat card row (responsive: 2 cols on tablet, 1 on mobile)
- Quick actions as vertical button stack in right panel
- Recent tasks table with compact rows

### Agent Chat
- Full-height layout (sidebar + chat area)
- Streaming text with cursor animation
- Debug panel collapsible on right
- RAG sources as collapsible accordion

### Workflow Designer
- Vue Flow canvas with dark background
- Custom node components with brand-colored ports
- YAML sync panel as side drawer

## Element Plus Overrides

The following EP components have custom styling in `index.scss`:
- `el-card` — dark bg, custom radius, hover lift
- `el-table` — dark header, striped rows, custom borders
- `el-button` — gradient primary, ghost secondary
- `el-input` / `el-select` — recessed dark bg, focus glow
- `el-dialog` — glass morphism header, dark body
- `el-menu` — sidebar gradient, active indicator
- `el-tag` — semantic color mapping, small size default
- `el-pagination` — dark theme adapted

## What NOT to Do

- ❌ Hardcode hex colors in `.vue` files — always use `var(--lumina-*)`
- ❌ Use `transition: all` — use `@include transition-colors()` or `@include transition-interact()` mixin
- ❌ Mix font families — stick to Outfit/IBM Plex/JetBrains Mono
- ❌ Create card styles per page — use consistent `--lumina-bg-card` + `--lumina-radius-md`
- ❌ Use `z-index: 9999` — use `--lumina-z-*` scale
- ❌ Forget empty/loading/error states
- ❌ Test only in light mode — dark is the primary theme
- ❌ Use `position: fixed` for Header/Sidebar — use **flex layout** (方案 A)
- ❌ Stack `overflow: hidden` on multiple ancestors — blocks horizontal scroll
- ❌ Put 6+ buttons in a table action column — use `el-dropdown` to fold extras
