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
└── packages/
    └── yak-ui/
```

`apps/web` 是产品 Web Root。

业务能力归 `app/<domain>`，后端通信归 `service/<domain>`，真正跨业务的基础能力才进入 root infrastructure。无业务语义 UI Primitive 归 `packages/yak-ui`。

## Web Root Ownership

```text
app        → Product Domain + Router + Layout
service    → Domain API + HTTP transport
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
yak-ops-ui/src
yak-ops-ui/public
packages/datasource
```

## Domain Structure

Datasource：

```text
app/datasource
├── management
├── editor
├── connection
├── plugin
├── model
└── i18n

service/datasource
├── api.ts
├── catalog.ts
├── driver.ts
└── index.ts
```

Login：

```text
app/login
service/auth
context/auth-context.tsx
hooks/use-auth.ts
```

页面负责展示，Service 负责后端通信，Context 负责跨组件运行时状态。

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

禁止恢复 `@yak-ops/datasource` package / alias。

## Theme Architecture

```text
themes/
├── tokens.css
├── light.css
├── dark.css
└── index.css

context/theme-context.tsx
hooks/use-theme.ts
types/theme.ts
```

Theme 是 Web 基础设施，不属于 Yak UI 或某个业务 Domain。

## Service Boundary

`service/http`
- 唯一 HTTP transport owner
- Result envelope
- network / authentication failure handling

`service/auth`
- Login
- Logout
- Current User

`service/datasource`
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

## Verification

```bash
cd yak-ops-ui
npm run check
npm run build
```
