# Lumina Design State v2 — "Studio Indigo"

> This file is the persistent source of truth for Lumina's UI design.
> The frontend design skill reads this before every UI task.
> Update this file when design tokens, patterns, or principles change.

## Brand Identity

- **Name**: Studio Indigo
- **Primary**: Indigo `#4f46e5` (Tailwind Indigo-600) — professional, trustworthy, modern
- **Accent**: Indigo `#6366f1` (Indigo-500, same family — NO separate warm accent)
- **Philosophy**: 企业级、克制、专业。参考 Linear / Notion 的设计语言——单一品牌色 + 中性灰 + 语义色。
- **Themes**: 亮色默认（`#ffffff` 基底）+ 暗色可选（`<html class="dark">` 切换）

## Migration Notes (v1 → v2)

| 维度 | v1 "Luminous" | v2 "Studio Indigo" |
|---|---|---|
| 主色 | 紫 #7c3aed × 琥珀 #f59e0b | 靛蓝 #4f46e5 单色 |
| 辅色 | 琥珀到处用 | 无独立辅色，靛蓝同系 |
| 渐变 | 到处双色渐变 | 仅 logo，其余纯色 |
| 发光 glow | 大量 0 0 20px | **全砍**，改 focus ring 0 0 0 3px |
| 主题 | 暗色唯一 | **亮色默认 + 暗色可切** |
| 组件 | EP 原子 + 1800 行覆盖 | `Lum*` 业务组件封装（Phase 2） |

## Color System

### Brand Colors (Tailwind Indigo)
| Token | Value | Usage |
|-------|-------|-------|
| `--lumina-primary` | `#4f46e5` | Buttons, links, active states, focus |
| `--lumina-primary-light` | `#818cf8` | Hover accents |
| `--lumina-primary-dark` | `#3730a3` | Pressed states |
| `--lumina-accent` | `#6366f1` | Secondary brand (same family) |

### Semantic Colors (亮暗通用)
| Token | Value | Usage |
|-------|-------|-------|
| `--lumina-success` | `#10b981` | Success messages, completed status |
| `--lumina-warning` | `#f59e0b` | Warnings, pending status |
| `--lumina-danger` | `#ef4444` | Errors, delete actions |
| `--lumina-info` | `#3b82f6` | Info messages |

### Surface Palette — 亮色（默认 `:root`）
| Token | Value | Usage |
|-------|-------|-------|
| `--lumina-bg-base` | `#ffffff` | Page background |
| `--lumina-bg-elevated` | `#f8fafc` | Panels, sidebar, header |
| `--lumina-bg-card` | `#ffffff` | Cards |
| `--lumina-bg-input` | `#ffffff` | Input fields |
| `--lumina-bg-hover` | `rgba(0,0,0,0.03)` | Hover overlay |
| `--lumina-bg-active` | `rgba(79,70,229,0.06)` | Active/selected |

### Surface Palette — 暗色（`.dark` 覆盖）
| Token | Value | Usage |
|-------|-------|-------|
| `--lumina-bg-base` | `#0f172a` | Page background |
| `--lumina-bg-elevated` | `#1e293b` | Panels, sidebar |
| `--lumina-bg-card` | `#1e293b` | Cards |
| `--lumina-bg-hover` | `rgba(255,255,255,0.04)` | Hover overlay |

### Text Hierarchy — 亮色
| Token | Value | Usage |
|-------|-------|-------|
| `--lumina-text-primary` | `#0f172a` | Headlines |
| `--lumina-text-regular` | `#334155` | Body text |
| `--lumina-text-secondary` | `#64748b` | Labels |
| `--lumina-text-muted` | `#94a3b8` | Placeholders |
| `--lumina-text-on-brand` | `#ffffff` | 白字印在 primary/success/warning/danger 等饱和背景上（亮暗通用，**勿用 text-inverse**——暗色下变深色会丢对比度） |

### Border — 亮色
| Token | Value | Usage |
|-------|-------|-------|
| `--lumina-border` | `#e2e8f0` | Default borders |
| `--lumina-border-light` | `#f1f5f9` | Subtle dividers |
| `--lumina-border-hover` | `#4f46e5` | Hover/focus borders |

## Typography

| Role | Font | Weight |
|------|------|--------|
| Display (titles) | Outfit | 600-700 |
| Body | IBM Plex Sans | 400-500 |
| Code/Mono | JetBrains Mono | 400 |

## Spacing Scale

4px (xs) → 8px (sm) → 16px (md) → 24px (lg) → 32px (xl) → 48px (2xl) → 64px (3xl)

## Motion

| Token | Duration | Usage |
|-------|----------|-------|
| `--lumina-transition-fast` | 150ms | Hover, toggle |
| `--lumina-transition-base` | 200ms | Card transitions |
| `--lumina-transition-slow` | 350ms | Page transitions |

Easing: `cubic-bezier(0.4, 0, 0.2, 1)` for all transitions.

## Shadow System (企业级 elevation — 去 glow)

| Token | Value | Usage |
|-------|-------|-------|
| `--lumina-shadow-sm` | `0 1px 2px rgba(0,0,0,0.05)` | Subtle elevation |
| `--lumina-shadow-md` | `0 4px 12px rgba(0,0,0,0.08)` | Cards, dropdowns |
| `--lumina-shadow-lg` | `0 8px 24px rgba(0,0,0,0.12)` | Modals |
| `--lumina-shadow-glow` | `0 0 0 3px rgba(79,70,229,0.12)` | Focus ring (NOT glow) |

## Theme Switching

- **默认**: 亮色（`:root` 定义所有 token）
- **暗色**: `<html class="dark">` 触发 `.dark` 选择器覆盖 token
- **切换**: `appStore.toggleTheme()` → `document.documentElement.classList.toggle('dark')`
- **持久化**: `localStorage('lumina-app')` via pinia-persist
- **初始化**: `main.ts` → `useAppStore().initTheme()` 在 mount 前应用
- **EP 暗色**: `element-plus/theme-chalk/dark/css-vars.css` 已引入

## Layout Architecture (方案 A — Flex)

- `#app { display: flex; height: 100vh; overflow: hidden }`
- `.lumina-layout { display: flex }` — Sidebar + Main 横向 flex
- `.main-container { flex: 1; min-width: 0 }` — 关键：允许收缩
- Header `position: sticky; top: 0`
- Sidebar `flex-shrink: 0; width: 220px/64px`
- **不用 fixed + margin-left 补偿**

## Component Patterns

### Sidebar
- 背景: `var(--lumina-bg-elevated)` 纯色（去渐变）
- Active indicator: 3px 左侧竖条 `var(--lumina-primary)`
- Collapse to 64px

### Header
- 背景: `var(--lumina-bg-base)` 纯色 + 1px border-bottom
- 主题切换按钮（Sun/Moon 图标）
- 语言切换 segmented button

### Tables
- Action column: 前 2 个高频按钮 + `el-dropdown` 折叠其余
- `overflow-x: auto` on page-content

### Buttons (v2 — 纯色企业级)
- Primary: `background: var(--lumina-primary)` 纯色，hover 变 `--lumina-primary-dark`
- **去渐变、去微光伪元素、去 glow shadow、去 translateY**

## Implemented Components (Phase 2 ✅)

| Component | Purpose | Usage |
|-----------|---------|-------|
| `LumTablePanel` | 搜索表单 + 表格 + 分页组合 | 配置式 searchFields + default slot 表格列 |
| `LumFormDialog` | 表单对话框标准封装 | v-model + rules + auto validate |
| `LumStatCard` | 统计卡片（Dashboard） | icon + label + value + 5 色变体 |
| `LumSearchInput` | 搜索输入（防抖 + icon） | v-model + @search |
| `LumEmptyState` | 空状态 | icon + description + action slot |

使用方式：`import { LumTablePanel, type SearchField } from '@/components/common'`

### LumTablePanel 迁移模式
- 标准 CRUD 页面：searchFields 配置式 + 表格列 default slot
- 无分页：不传 `:pagination` prop
- 特殊控件（auto-refresh/input-number）：放 `toolbar-right` slot
- 迁移 7 个页面：agent/prompt/workflow/task + system/tenant/role/user

## Responsive

- **断点**：`sm:640 / md:768 / lg:1024`（mixins.scss `$breakpoints`）
- **scss 文件**：用 `@include respond-below('md')` / `respond-below('sm')`
- **css 文件**（`<style scoped>` 无 lang）：直接写 `@media (max-width: 767px)`（md-1）/ `@media (max-width: 639px)`（sm-1）
- **必断点**：任何含固定宽度面板（≥200px）或 ≥3 列 grid 的组件，必须有 `respond-below('md')` 退化方案
- **窄屏 dialog**：el-dialog 默认 `max-width: calc(100% - 30px)` 会自动收缩，固定 px width 在窄屏不溢出，无需特殊处理

## What NOT to Do

- ❌ Hardcode hex colors in `.vue` files — use `var(--lumina-*)`
- ❌ Use `transition: all` — use `@include transition-colors()` mixin
- ❌ Use `position: fixed` for Header/Sidebar — use flex
- ❌ Use `box-shadow: 0 0 20px` glow — use elevation shadow or focus ring
- ❌ Use gradient backgrounds on buttons — use solid colors
- ❌ Define scoped `:root` token overrides in page components
- ❌ Stack `overflow: hidden` on multiple ancestors
- ❌ Put 6+ buttons in a table action column — use `el-dropdown`
- ❌ Mix font families — stick to Outfit/IBM Plex/JetBrains Mono
- ❌ Use `z-index: 9999` — use `--lumina-z-*` scale
- ❌ Use `var(--lumina-text-inverse)` for text on saturated brand/semantic backgrounds — use `--lumina-text-on-brand`（inverse 在暗色下变深色，丢对比度）
- ❌ Use `var(--lumina-bg-secondary, #f5f7fa)` —— **该 token 不存在**，永远回退浅色。用 `--lumina-bg-elevated`
- ❌ Hardcode `rgba(15,23,42,*)` / `rgba(51,65,85,*)`（slate 暗色）—— 暗色残留，用 `--lumina-bg-mask` / `--lumina-border-light`
