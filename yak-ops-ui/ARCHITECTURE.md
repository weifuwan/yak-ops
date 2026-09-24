# Yak Ops UI Architecture

Status: Implementing

Scope:
- `yak-ops-ui/src/**`
- Login
- Datasource

Depends On:
- `../ARCHITECTURE.md`
- `./FRONTEND_RULES.md`

## Principle

Yak Ops UI 按 ownership 组织代码，不按文件类型堆目录。

当前产品范围只有：

```text
Login
Datasource
```

Login 是支撑能力，Datasource 是当前唯一产品能力。

不要因为历史代码、旧目录或未来规划重新引入其它产品域。

## Target Structure

目标结构与 Yakable 保持同一套前端骨架：

```text
src/
├── app/
│   ├── App.tsx
│   ├── providers/
│   ├── router/
│   ├── layout/
│   └── styles/
├── pages/
│   ├── login/
│   └── data-source/
├── features/
│   └── datasource/
├── service/
│   ├── http/
│   ├── auth/
│   └── datasource/
├── shared/
│   ├── lib/
│   └── ui/
└── main.tsx
```

`features` 不是必选层。

只有一个能力拥有独立状态、交互或复用边界时，才进入 Feature。简单页面行为直接留在 Page，不为了目录对称增加 Feature。

## Current Migration State

PR1 已完成脚手架迁移：

```text
Umi Max
→ Vite
→ React Router
→ Tailwind CSS 4
→ TypeScript 5.9
→ Oxlint / Oxfmt
```

当前仍存在以下迁移目录：

```text
src/services/
src/components/
src/utils/
```

它们是当前代码事实，不是最终 ownership 结构。

后续 Login / Datasource 迁移时分别收口到：

```text
services  → service
components → page / feature / shared/ui
utils      → service/http / shared/lib
```

不要为了本 PR 一次性搬空这些目录。

## Dependency Direction

目标依赖方向：

```text
app
 ↓
pages
 ↓
features
 ↓
service

pages ─────→ service
pages ─────→ shared
features ──→ shared
service ───→ shared/lib
```

Feature 层只有真实 owner 时才存在，所以 `pages → service` 是允许的。

禁止反向依赖：

```text
shared  ✕→ feature
service ✕→ page
service ✕→ feature
feature ✕→ page
page    ✕→ app
```

## app

`src/app` 拥有应用级组合：

- `App.tsx`：根组合入口。
- `providers`：认证等全局运行时 Provider。
- `router`：React Router 路由注册与路由守卫。
- `layout`：全局布局、侧边栏和 Outlet。
- `styles`：reset、字体、主题和全局 viewport 样式。

`main.tsx` 只挂载 React 应用。

app 不拥有 Datasource 业务规则。

## pages

Page 负责回答“这个页面展示什么”。

当前只允许：

```text
src/pages/login
src/pages/data-source
```

Page 可以拥有：

- 页面组合。
- 页面局部交互状态。
- 页面私有 Hook。
- 页面私有组件。

Page 不拥有：

- HTTP transport。
- 后端 Result 协议解析。
- 跨页面通用组件。
- 可独立复用的 Datasource 能力。

## features

Feature 负责拥有一个明确的产品能力，而不是减少 Page 文件长度。

只有满足以下至少一个条件才创建 Feature：

- 有独立状态生命周期。
- 有多个真实调用方。
- 有稳定交互 Contract。
- 能脱离某个具体 Page 被独立理解和验证。

当前不要预创建空的 Auth / Datasource Feature。

## service

目标目录为 `src/service`。

所有后端接口、请求响应 Contract 和 HTTP 协议适配都归 Service。

固定调用方向：

```text
Page / Feature
→ Domain Service
→ HttpUtils
→ Backend API
```

详细规则见 `SERVICE_RULES.md`。

## shared

`shared` 只放没有产品 owner 的复用能力。

```text
shared/lib
→ 浏览器通用工具、纯函数、基础 infrastructure

shared/ui
→ 无业务语义的通用 UI primitive
```

Shared 不知道 Login、Datasource、Project、Session、Workflow 等产品语义。

一个组件只被 Datasource 使用，不代表它应该进入 Shared。

## State Boundary

状态按事实来源归属：

- URL 已表达的状态归 URL。
- 后端业务事实以 Service 返回值为准。
- 当前交互拥有的可变 UI 状态使用 React state。
- 应用级认证状态归 `app/providers`。
- 不为了减少 props 提前引入全局 Store。

不要复制同一份状态形成第二事实来源。

## Routing Boundary

React Router 是唯一浏览器路由 owner。

- 路由注册只在 `app/router`。
- Page / Feature 使用 React Router API。
- 不直接维护 `window.history` / `popstate`。
- 当前只发布 `/login` 与 `/data-source`。
- 未发布能力不创建隐藏路由或占位菜单。

## Asset Boundary

资源按 owner 放置：

- app 全局资源 → `app/styles` 或 `src/assets`。
- Page 私有资源 → Page 内。
- Feature 私有资源 → Feature 内。
- 需要稳定 URL 的资源才进入 `public/`。

不要重新创建按文件类型划分的 `font/image/css` 顶层桶。

## Current Non-Goals

当前不引入：

- Zustand。
- TanStack Query。
- Redux。
- 第二套路由框架。
- 第二套 HTTP Client。
- 新的产品页面。
- 为了目录对称创建空 Feature。

这些能力必须由真实问题驱动。

## Verification

架构规则由可执行工具约束：

```bash
cd yak-ops-ui
npm run check
npm run build
```

工具定义见 `docs/tooling.md`。
