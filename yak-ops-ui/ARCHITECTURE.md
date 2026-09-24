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
```

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

Service 不得为了复用 TypeScript 类型反向 import App。

## Datasource Contract Ownership

Datasource 后端 Contract 归：

```text
service/datasource/types.ts
```

`app/datasource/model/types.ts` 只作为 App 内部的 type re-export facade，真实类型 owner 仍是 Service。

这样依赖方向保持：

```text
app/datasource
      ↓
service/datasource/types
```

而不是形成 `app ↔ service` 环。

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

架构不只靠文档约定。

```bash
npm run architecture:check
```

由 `scripts/check-architecture.mjs` 检查稳定 invariant。

`npm run check` 固定顺序：

```text
architecture
→ typecheck
→ lint
→ format
```

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
