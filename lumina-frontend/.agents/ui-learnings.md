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

## 2026-07-09 — Fixed 布局的三大陷阱（方案 C → 方案 A 迁移）
- Observation: Header/Sidebar 都用 `position: fixed` 导致：① z-index 战争（Sidebar 1001 > Header 1000 遮挡折叠按钮）② 需 margin-left + padding-top 手动补偿 ③ 三层 `overflow:hidden` 锁死横向滚动
- Action: 中后台布局统一用 **flex**（方案 A）：`.lumina-layout { display: flex }` + Sidebar `flex-shrink:0` + Header `sticky` + `.main-container { flex:1; min-width:0 }`。`min-width:0` 是允许 flex 子项收缩的关键
- Confidence: high

## 2026-07-09 — transition: all 的标准化替代
- Observation: index.scss 有 13 处 `transition: all var(--lumina-transition-xxx)`，违反 DESIGN.md 且影响性能（触发不必要的重绘）
- Action: 在 mixins.scss 加 `@mixin transition-colors($duration)` 和 `@mixin transition-interact($duration)`（含 transform），批量替换。Button 用 interact（有 translateY），其余用 colors
- Confidence: high

## 2026-07-09 — 表格操作列 dropdown 折叠模式
- Observation: prompt/workflow 主表 6 个按钮塞 280px 列，user 5 个塞 320px，英文 i18n 后更糟
- Action: 按钮 ≥ 4 时，前 2 个高频按钮直接显示 + `el-dropdown` 折叠其余。`@command="(cmd, row) => handleRowCommand(cmd, row)"` 统一分发。列宽收到 180-200px。`v-permission` 在 `el-dropdown-item` 上同样生效
- Confidence: high

## 2026-07-09 — v2 设计语言迁移 "Studio Indigo"
- Observation: v1 "Luminous"（紫×琥珀双色渐变 + 暗色唯一 + glow 泛滥）太花哨，不符合企业级定位
- Action: 整体迁移到 Tailwind Indigo #4f46e5 单色 + 亮色默认/暗色可切 + 去 glow（改 focus ring）+ 去 button 渐变（改纯色）。variables.scss 完全重写，:root 为亮色，.dark 覆盖暗色。EP 的 dark/css-vars.css 已引入配合 .dark class
- Confidence: high

## 2026-07-09 — 主题切换实现要点
- Observation: 从"暗色唯一"改"亮色默认+暗色可切"时，scoped :root 重复定义会覆盖全局 token
- Action: ① 删除 login/dashboard 的 scoped :root 重复定义（它们强制暗色）② app store 加 theme 状态 + applyTheme 操作 document.documentElement.classList ③ main.ts 在 pinia 后 initTheme() 恢复 localStorage ④ EP 的 dark/css-vars.css 配合 html.dark 自动切换 EP 组件
- Confidence: high

## 2026-07-09 — LumTablePanel 迁移的标签嵌套陷阱
- Observation: 从 el-card+el-table 迁移到 LumTablePanel 时，`</el-table></el-card>` 被替换为 `</LumTablePanel>`，但原来的最后一个 `</el-table-column>`（操作列关闭）仍保留，导致 `</el-table-column>` 出现两次，Vue SFC 编译报 "Invalid end tag"
- Action: 迁移时检查操作列的 `</el-table-column>` 是否重复。vue-tsc 不报此错，只有 vite build 会报
- Confidence: high

## 2026-07-09 — 登录页暗色→亮色重做
- Observation: 登录页整体是暗色设计（glow orbs + glass morphism + 暗色卡片/输入框 + 渐变按钮），简单替换颜色不够——整体视觉结构需要重做
- Action: 完全重写：去 glow orbs/glass/渐变/pulse 动画，改用 --lumina-bg-page 浅灰背景 + 白色实色卡片 + 标准 EP 输入框 + 纯色按钮。493→210 行。间距/字体全部 token 化
- Confidence: high

## 2026-07-09 — 废弃 CSS 清理时机
- Observation: dashboard 迁移到 LumStatCard 后，28 处 stat-card CSS 成为死代码（129 行），但不影响功能。留着增加维护噪音
- Action: 迁移组件后立即清理废弃 CSS，不要留到"以后"。用 PowerShell `[System.IO.File]::ReadAllLines` + 按行删除大范围 CSS 最可靠
- Confidence: high

## 2026-07-20 — 功能迭代期的暗色硬编码残留
- Observation: v1→v2 主题迁移做了 variables.scss 重写，但 dashboard/workflow designer 等后期加的功能页仍残留 v1 暗色硬编码（rgba(51,65,85)、rgba(15,23,42)、#dcdfe6、#fff 节点背景），亮色主题下是突兀深色横线/黑遮罩，暗色下 designer 节点是白块
- Action: 加功能时颜色必须 grep 确认用了 token。批量扫描：`node` 脚本扫 `.vue`/`.scss` 的 `#[0-9a-f]` 和 `rgba(15|51|224...` 即可定位残留。loading 遮罩用 `var(--lumina-bg-mask)`，表格边框用 `var(--lumina-border-light)`
- Confidence: high

## 2026-07-20 — 不存在的 token 与 CSS var 回退陷阱
- Observation: webhooks.vue/api-tokens.vue 写了 `var(--lumina-bg-secondary, #f5f7fa)`，但 `--lumina-bg-secondary` 从未定义，永远回退 #f5f7fa 浅灰，暗色下刺眼。CSS var 带回退值会掩盖"token 不存在"的 bug
- Action: 用 token 前先 grep variables.scss 确认存在。另外 `--lumina-text-inverse` 在暗色是 #0f172a（深色），印在饱和品牌色按钮上会丢对比度——品牌色上的白字必须用 `--lumina-text-on-brand`（亮暗同值 #fff）
- Confidence: high
