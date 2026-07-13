# 前端 UI 问题排查报告

## 背景

本报告基于当前前端代码做静态排查，重点关注遮挡、按钮显示不全、固定布局、溢出裁切、响应式缺失等 UI 细节问题。排查未修改 UI 实现，仅记录风险点、代码位置和可能原因。

排查参考：

- `.agents/skills/lumina_frontend_design/SKILL.md`
- `lumina-frontend/DESIGN.md`
- `lumina-frontend/.agents/ui-learnings.md`
- `lumina-frontend/src/assets/styles/variables.scss`
- `lumina-frontend/src/assets/styles/index.scss`

## 关键结论

当前 UI 问题主要集中在四类：

- Header 与 Sidebar 都使用 fixed 定位，层级和 left 起点不一致，导致 Header 左侧按钮和面包屑被 Sidebar 遮挡。
- Layout 多层 `overflow: hidden` 限制横向溢出，表格操作列按钮超宽后容易被裁切。
- 表格操作列大量使用固定宽度，部分页面按钮数量明显超过列宽承载能力。
- Sidebar 当前样式缺少菜单滚动、折叠态和子菜单层级覆盖，菜单多时容易显示不全。

## P0 问题

### Header 左侧被 Sidebar 遮挡

涉及文件：

- `lumina-frontend/src/layouts/components/AppHeader.vue`
- `lumina-frontend/src/layouts/components/AppSidebar.vue`

代码特征：

- `AppHeader.vue` 中 `.app-header` 使用 `position: fixed; left: 0; right: 0; z-index: 1000`。
- `AppSidebar.vue` 中 `.app-sidebar` 使用 `position: fixed; width: 220px; z-index: 1001`，折叠时宽度为 `64px`。

影响：

- Header 虽然渲染在主内容容器里，但 fixed 后脱离父容器，仍从视口左侧开始。
- Sidebar 层级高于 Header，会覆盖 Header 左侧区域。
- Header 左侧的折叠按钮和面包屑存在不可见、显示不全或点击不到的风险。

高概率表现：

- 折叠按钮被侧栏遮挡。
- 折叠后按钮仍在侧栏 64px 覆盖区域内。
- 面包屑左侧内容被挡住。

### 顶部内容与 fixed Header 挤压

涉及文件：

- `lumina-frontend/src/layouts/components/AppHeader.vue`
- `lumina-frontend/src/layouts/DefaultLayout.vue`

代码特征：

- Header fixed 后不占文档流高度。
- `DefaultLayout.vue` 内同时存在 Header 面包屑和独立 `breadcrumb-bar`。
- `breadcrumb-bar` 设置 `top: 56px; z-index: 999`，但布局没有统一给 fixed Header 预留高度。

影响：

- 页面顶部区域存在 Header、breadcrumb bar、页面内容三者挤压或重叠风险。
- 首屏页面标题区或顶部操作按钮可能看起来被压住。
- 两套面包屑同时存在，增加顶部高度和视觉重复。

### 多层 overflow hidden 导致横向裁切

涉及文件：

- `lumina-frontend/src/App.vue`
- `lumina-frontend/src/layouts/DefaultLayout.vue`

代码特征：

- `#app` 使用 `height: 100vh; overflow: hidden`。
- `.lumina-layout` 和 `.main-container` 都使用 `height: 100vh; overflow: hidden`。
- `.page-content` 使用 `overflow-y: auto; overflow-x: hidden`。

影响：

- 页面仅允许 `.page-content` 纵向滚动。
- 表格、固定右侧操作列、宽按钮组、宽 Dialog/Drawer、设计器画布等横向超出时会被直接裁切。
- 用户无法通过横向滚动访问被裁切的按钮。

## P1 问题

### 表格操作列宽度不足

涉及文件：

- `lumina-frontend/src/views/prompt/index.vue`
- `lumina-frontend/src/views/workflow/index.vue`
- `lumina-frontend/src/views/system/user.vue`
- `lumina-frontend/src/views/system/role.vue`
- `lumina-frontend/src/views/system/permission.vue`
- `lumina-frontend/src/views/system/tenant.vue`

典型风险：

- `prompt/index.vue` 操作列 `width="280"`，最多包含 6 个按钮：版本、编辑、发布、新版本、复制、删除。
- `workflow/index.vue` 操作列 `width="280"`，最多包含 6 个按钮，其中“可视化编辑”文字较长。
- `system/user.vue` 操作列 `width="320"`，最多包含 5 个 link 按钮：编辑、分配角色、重置密码、启用/禁用、删除。

影响：

- 按钮在固定列中换行、压缩或被裁切。
- 英文 i18n 文案通常比中文更长，切换语言后风险更高。
- 权限控制导致按钮集合动态变化，局部测试不一定覆盖所有组合。

### 小宽度操作列存在国际化裁切风险

涉及文件：

- `lumina-frontend/src/views/budget/index.vue`
- `lumina-frontend/src/views/knowledge/index.vue`
- `lumina-frontend/src/views/knowledge-base/index.vue`
- `lumina-frontend/src/views/task/index.vue`
- `lumina-frontend/src/views/evaluation/index.vue`

典型风险：

- 多个操作列宽度为 `80px`，承载 `Delete`、`Unmount` 等英文按钮时偏紧。
- `task/index.vue` 操作列 `width="120"`，可能同时出现“详情 + 取消”。
- `evaluation/index.vue` 部分操作列 `width="90"` 或 `width="170"`，后续增加按钮后容易越界。

影响：

- 当前中文下可能勉强正常，但英文切换后出现裁切。
- Element Plus button padding 与字体变化会放大问题。

### Header 右侧缺少响应式收缩策略

涉及文件：

- `lumina-frontend/src/layouts/components/AppHeader.vue`

代码特征：

- `.header-left` 使用 `flex: 1`，但缺少 `min-width: 0`。
- `.header-right` 固定横排展示语言切换、主题、通知、头像。
- Header 面包屑缺少最大宽度、省略和隐藏策略。

影响：

- 窄屏或长英文面包屑下，Header 左右两侧互相挤压。
- 右侧按钮可能被推出视口或显示不全。
- Header 本身 fixed，不参与普通布局，溢出问题更明显。

### PageHeader 操作区不换行

涉及文件：

- `lumina-frontend/src/components/common/PageHeader.vue`
- `lumina-frontend/src/views/workflow/index.vue`

代码特征：

- `.page-header-actions` 使用 `display: flex; flex-shrink: 0`，未设置换行。
- `workflow/index.vue` 的 PageHeader actions 同时有 3 个按钮。
- 仅在 `max-width: 640px` 时改为纵向堆叠。

影响：

- 641px 到中等宽度区间仍可能横向挤压。
- 英文文案更长时，右侧按钮组容易溢出或被裁切。

### Sidebar 菜单缺少滚动

涉及文件：

- `lumina-frontend/src/layouts/components/AppSidebar.vue`

代码特征：

- `.app-sidebar` 使用 `overflow: hidden`。
- `:deep(.el-menu)` 只设置 `flex: 1` 和 `padding`，没有 `overflow-y: auto`。

影响：

- 后端动态菜单较多或子菜单展开较多时，底部菜单会被直接裁掉。
- 用户无法滚动访问底部菜单项。

### Sidebar 折叠态缺少专项覆盖

涉及文件：

- `lumina-frontend/src/layouts/components/AppSidebar.vue`

代码特征：

- 模板使用 `:collapse="appStore.sidebarCollapsed"`。
- 当前样式未覆盖 `.el-menu--collapse .el-menu-item` 和 `.el-menu--collapse .el-sub-menu__title`。

影响：

- 折叠状态下 Element Plus 默认 padding 可能仍按展开菜单计算。
- 图标可能不居中，hover 背景和 active 指示条位置可能偏移。

### Sidebar 子菜单层级样式缺失

涉及文件：

- `lumina-frontend/src/layouts/components/AppSidebar.vue`

代码特征：

- 模板存在二级菜单。
- 当前样式缺少 `.el-menu--inline`、子菜单缩进、子菜单背景、打开态样式。

影响：

- 子菜单层级视觉弱。
- 二级菜单标题较长时，容易与一级菜单产生视觉挤压。

## P2 问题

### 局部硬编码颜色与设计系统冲突

涉及文件：

- `lumina-frontend/src/layouts/components/AppHeader.vue`
- `lumina-frontend/src/layouts/components/AppSidebar.vue`

代码特征：

- 局部样式中存在大量 `#0f172a`、`#1a1040`、`#94a3b8`、`#f1f5f9`、`#a78bfa`、`#7c3aed`、`#f59e0b` 和 `rgba(...)`。
- 设计规范要求优先使用 `--lumina-*` token。

影响：

- 不是遮挡主因，但会导致主题一致性和后续维护问题。
- 当前 Sidebar 样式像是从旧设计回退后的不完整版本。

### 全局 Button 覆盖使用 transition all

涉及文件：

- `lumina-frontend/src/assets/styles/index.scss`

代码特征：

- `.el-button` 使用 `transition: all var(--lumina-transition-base)`。
- `DESIGN.md` 明确要求不要使用 `transition: all`。

影响：

- 不是遮挡主因。
- 在表格 fixed 列和按钮 hover 场景中可能带来不必要的重绘或视觉抖动。

### 移动端布局不完整

涉及文件：

- `lumina-frontend/src/layouts/DefaultLayout.vue`
- `lumina-frontend/src/layouts/components/AppHeader.vue`
- `lumina-frontend/src/layouts/components/AppSidebar.vue`
- `lumina-frontend/src/views/evaluation/index.vue`

代码特征：

- `DefaultLayout.vue` 移动端仅调整 `.main-container` 的 `margin-left`。
- `AppHeader.vue` 没有移动端 actions 收缩策略。
- `AppSidebar.vue` 中媒体查询针对 `:deep(.el-col)`，与 Sidebar 组件职责不匹配。
- `evaluation/index.vue` 使用固定 `el-col :span="10"` 和 `:span="14"`，没有 `xs/sm/md` 响应式配置。

影响：

- 窄屏下侧栏、Header、内容区没有形成完整可用的布局方案。
- 表格、按钮组和面板布局更容易挤压裁切。

## 后续处理建议

建议按以下顺序修复：

1. 统一 Layout 定位策略：确定 Header 是否 fixed，以及 fixed 时是否按 Sidebar 宽度设置 `left`。
2. 给 fixed Header 预留明确空间，避免顶部内容被压住。
3. 处理表格操作列：多按钮列改为更宽、可换行、下拉更多操作，或允许表格横向滚动。
4. 恢复 Sidebar 菜单滚动、折叠态和子菜单层级样式。
5. 给 Header、PageHeader、重点页面补中小屏响应式策略。
6. 逐步清理局部硬编码颜色，回归 `--lumina-*` token。

## 验证建议

修复后建议至少覆盖以下场景：

- 桌面展开侧栏：检查 Header 折叠按钮、面包屑、右侧操作区。
- 桌面折叠侧栏：检查 Header 左侧是否仍被 64px 侧栏遮挡。
- 1366px、1024px、768px、375px 视口：检查 Header、PageHeader、表格操作列。
- 中文和英文语言切换：重点检查操作列按钮宽度。
- 权限按钮全量展示：检查用户、角色、权限、Prompt、Workflow 列表操作区。
- 动态菜单较多和子菜单展开：检查 Sidebar 是否可滚动到底部。
