# Yak Ops UI Architecture

Status: Active

Scope:
- `yak-ops-ui/apps/**`
- `yak-ops-ui/packages/**`

Depends On:
- `../ARCHITECTURE.md`
- `./FRONTEND_RULES.md`

## Principle

Yak Ops UI 使用 Workspace + Dify-style Web Root。

```text
yak-ops-ui/
├── apps/
│   └── web/
│       ├── app/
│       │   ├── datasource/
│       │   ├── login/
│       │   ├── layout/
│       │   └── router/
│       ├── service/
│       │   ├── auth/
│       │   ├── datasource/
│       │   └── http/
│       ├── utils/
│       ├── themes/
│       ├── types/
│       ├── hooks/
│       ├── context/
│       ├── config/
│       ├── constants/
│       ├── assets/
│       └── public/
├── packages/
│   └── yak-ui/
└── scripts/
    └── check-architecture.mjs
```

`apps/web` 是产品 Web Root。

业务能力归 `app/<domain>`，后端通信与后端 Contract 归 `service/<domain>`，真正跨业务的基础能力才进入 root infrastructure。无业务语义 UI Primitive 归 `packages/yak-ui`。

## Domain Locality

Domain 内优先局部内聚，不把概念名自动变成目录层级。

Datasource 当前页面结构：

```text
app/datasource/
├── index.tsx
├── card.tsx
├── toolbar.tsx
├── summary.tsx
├── empty-state.tsx
├── constants.tsx
├── types.ts
├── utils.ts
├── hooks/
├── editor/
├── connection/
├── icons/
└── i18n/
```

`management / model / plugin` 已删除，因为它们只是概念分层，不是独立产品能力。

## Dependency Direction

```text
app/router
   ↓
app/datasource ─────→ packages/yak-ui
   ↓
service/datasource
   ↓
service/http

app/login ──────────→ packages/yak-ui
   ↓
service/auth
   ↓
service/http
```

核心 invariant：

```text
app → service → http
```

禁止：

```text
service → app
app → service/http
```

## Datasource Contract Ownership

Datasource 后端 Contract 归：

```text
service/datasource/types.ts
```

App 通过：

```text
app/datasource/types.ts
```

消费和补充 UI-only 类型。

不要重新创建 `model/types.ts` 或第二份 Contract owner。

## Web Root Ownership

```text
app        → Product Domain + Router + Layout
service    → Domain API + Backend Contract + HTTP transport
utils      → 无业务工具
themes     → Theme Token / light / dark
types      → 跨 Web 稳定类型
hooks      → 跨组件 React Hook
context    → App-wide Context
config     → 运行配置
constants  → 稳定常量
assets     → 参与构建的资源
public     → 原样静态资源
```

禁止重新创建：

```text
apps/web/src
apps/web/pages
apps/web/shared
yak-ops-ui/src
yak-ops-ui/public
yak-ops-ui/types
yak-ops-ui/mock
packages/datasource
app/datasource/management
app/datasource/model
app/datasource/plugin
```

## Service Boundary

`service/http`
- 唯一 HTTP transport owner
- Result envelope
- network / authentication failure handling
- 唯一允许调用原生 `fetch` 的位置

`service/auth`
- Login
- Logout
- Current User

`service/datasource`
- Datasource Contract
- Datasource CRUD
- Connection Test
- Plugin Config
- Catalog
- Driver Upload

Component / Domain Hook 禁止直接调用 `fetch`。

## Package Boundary

现在只保留真正独立的 UI Package：

```text
packages/yak-ui
```

Datasource 是产品 Domain，不再作为 npm workspace package。

Workspace root 不拥有运行时 dependencies；运行时依赖由 `apps/web` / `packages/yak-ui` 分别声明。

## Architecture Enforcement

```bash
npm run architecture:check
```

由 `scripts/check-architecture.mjs` 检查稳定 invariant。

架构变化必须同时更新：

```text
ARCHITECTURE.md
*_RULES.md
check-architecture.mjs
```

## Verification

```bash
cd yak-ops-ui
npm run check
npm run build
```
