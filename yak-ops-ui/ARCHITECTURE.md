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

`apps/web` 是产品 Web Root。

业务能力归 `app/<domain>`，后端通信与后端 Contract 归 `service/<domain>`，真正跨业务的基础能力才进入 root infrastructure。无业务语义 UI Primitive 归 `packages/yak-ui`。

## Domain Locality

Domain 内优先局部内聚，不把概念名自动变成目录层级。

Datasource 当前结构：

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
│   ├── index.tsx
│   ├── type-selector.tsx
│   ├── dynamic-form.tsx
│   ├── custom-kv-list.tsx
│   ├── form-runtime.tsx
│   ├── form-model.ts
│   ├── form-utils.ts
│   ├── driver-manager.tsx
│   ├── jdbc-url-field.tsx
│   ├── jdbc-url-utils.ts
│   ├── ssh-tunnel-manager.tsx
│   └── types.ts
├── icons/
└── i18n/
```

`management / model / plugin / connection` 已删除。

Datasource Editor 允许一层独立目录，因为它本身足够复杂；Editor 内部继续保持扁平。

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

App 通过 `app/datasource/types.ts` 消费并补充 UI-only 类型。

Editor 私有 Contract 归 `app/datasource/editor/types.ts`。

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
app/datasource/connection
app/datasource/editor/DynamicDataSourceForm
```

## Service Boundary

`service/http` 是唯一 HTTP transport owner。

`service/auth` 拥有 Login / Logout / Current User。

Datasource Service 保持局部内聚：

```text
service/datasource/
├── index.ts
└── types.ts
```

- `index.ts`：CRUD、Connection Test、Plugin Config、Catalog、Driver Upload。
- `types.ts`：稳定 backend Contract。

Service 不按 endpoint 概念机械拆文件；只有形成独立协议、独立生命周期或明显复杂度时才继续拆。

## Package Boundary

现在只保留真正独立的 UI Package：

```text
packages/yak-ui
```

## Architecture Enforcement

```bash
npm run architecture:check
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
