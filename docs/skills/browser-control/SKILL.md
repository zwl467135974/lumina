---
name: browser-control
description: 用 Playwright 浏览器工具完成打开网页、读取内容、表单交互与截图留证；适用于网页信息采集、Web 界面验证、在线文档读取等需要真实浏览器环境的任务
whenToUse: 当任务需要访问网页、验证 Web 页面行为或对页面截图留证时使用；纯文本抓取优先考虑 util.http，需要 JS 渲染或交互操作时才用浏览器
---

# 浏览器控制（Playwright）

通过 MCP 接入的官方 `@playwright/mcp` 工具面驱动真实浏览器。工具命名规则为
`mcp__{server}__{tool}`，下文以 server 名 `playwright` 为例（实际前缀以当前
环境的 MCP 配置为准）。

## 核心工作流：快照 → 定位 → 操作 → 留证

1. **打开页面**：`mcp__playwright__browser_navigate(url)`。等待加载完成后
   先取结构化快照，不要急于截图。
2. **读取结构**：`mcp__playwright__browser_snapshot`。返回页面的可访问性
   树，每个可交互元素带 `ref` 编号——这是后续操作的定位依据。
3. **定位操作**：`browser_click(element, ref)`、`browser_type(element, ref, text)`、
   `browser_fill_form(fields)`、`browser_select_option(element, ref, values)`、
   `browser_press_key(key)`。**必须使用快照中的 ref 定位，不要凭记忆猜
   CSS 选择器**；页面变化后重新取快照再操作。
4. **视觉留证**：`browser_take_screenshot` 仅在需要视觉证据（布局、颜色、
   渲染问题）时使用；结构化信息一律以快照为准，省 token 且更精确。

## 页面状态与多标签

- 弹出对话框（alert/confirm/prompt）：先 `browser_handle_dialog(accept)`，
  不要让对话框阻塞后续操作。
- 新窗口/弹出页：操作后用 `browser_tabs(action: "list")` 观察全部标签页，
  `browser_tabs(action: "select", index: n)` 切换。
- 等待动态内容：`browser_wait_for(text=..., time=...)`，不要连续无脑重试。

## 排障顺序

页面不符合预期时按序排查：

1. 重新 `browser_snapshot` 确认当前真实状态（可能已被重定向）；
2. `browser_console_messages(level: "error")` 看前端报错；
3. `browser_network_requests` 确认关键请求是否成功（4xx/5xx/超时）；
4. 仍无头绪再截图人工判读。

## 纪律

- **快照优先**：能用快照解决的绝不用截图，能用文本绝不用视觉。
- **ref 时效**：任何 click/type 之后页面可能变化，长流程中定期重新快照。
- **数据落库**：采集到的结构化数据写入任务结果或文件，不要只留在对话里。
- **退出**：任务结束调用 `browser_close` 释放浏览器进程。

## 边界

- 浏览器运行在 Lumina 服务端所在主机，访问目标受部署层网络策略约束；
  内网/环回地址可能被 `--blocked-origins` 拦截，属预期行为。
- 文件系统访问默认限定工作区根目录，`file://` 导航被禁用。
