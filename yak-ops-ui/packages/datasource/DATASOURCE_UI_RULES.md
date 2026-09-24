# Datasource UI Rules

Scope:
- `yak-ops-ui/packages/datasource/**`

Owns:
- Datasource frontend package boundary
- Datasource product-facing public API

## Current Migration State

PR1 先建立 `@yak-ops/datasource` 作为 App 唯一 Datasource 入口。

当前 `src/index.tsx` 临时桥接既有 `src/pages/data-source` 实现，以保证 Workspace / App migration 不改变用户行为。

后续 Datasource Package Refactor 再把实现按真实能力迁入本 package：

```text
management
editor
connection
plugin
model
api
```

## Must

- App 只从 package public entry 使用 Datasource。
- Datasource 可以依赖 `@yak-ops/yak-ui`。
- 内部目录按 capability owner 组织，不按 components/hooks/utils 文件类型分桶。

## Must Not

- 依赖 `apps/web`。
- 直接依赖 Router / AppLayout / AuthProvider。
- 把通用 Button / Input / Select / Dialog 等 Primitive 放进 Datasource。
- 在 PR1 提前改变 Datasource 业务行为。

## Dependency Direction

```text
apps/web
   ↓
@yak-ops/datasource
   ↓
@yak-ops/yak-ui
```
