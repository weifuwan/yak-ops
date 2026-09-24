# Datasource UI Rules

Scope:
- `yak-ops-ui/packages/datasource/**`

Status:
- Temporary owner until PR2 App Package Migration

Owns:
- Datasource frontend product capability
- Datasource API contract/adaptation
- Datasource management / editor / connection / plugin behavior
- Datasource dynamic form runtime

## Structure

```text
src/
├── api/
├── connection/
├── editor/
├── i18n/
├── management/
├── model/
├── plugin/
└── index.tsx
```

Capability owns its component, hook, type and helper。不要重新创建顶层 `components / hooks / utils / types` 大桶。

## Dependency Direction

```text
apps/web
   ↓
@yak-ops/datasource
   ↓
management / editor / connection / plugin
   ↓
api + model
   ↓
@yak-ops/yak-ui
```

PR1 到 PR2 之间，`api` 可临时通过 `@/service/http/HttpUtils` 使用 Web Root 的唯一 HTTP transport。这是唯一允许的 App bridge，PR2 Datasource 迁入 `app/datasource` 后必须删除。

## Must

- App 只通过 package public entry 进入 Datasource。
- Common UI primitives 来自 `@yak-ops/yak-ui`。
- Internal directories follow capability ownership。
- Product state stays with the capability that owns the behavior。
- Backend request/response adaptation stays under `api`。
- Cross-capability stable contracts stay under `model`。

## Must Not

- 除临时 HTTP transport bridge 外依赖 `apps/web` 的 Router、Layout、Context 或页面。
- Import 已删除的根 `src/**`。
- Import `antd` 或 `@ant-design/icons`。
- Recreate Ant Design APIs or compatibility wrappers。
- Recreate generic Button / Input / Select / Dialog primitives。
- Add top-level generic `components / hooks / utils / types` buckets。

## UI Boundary

```text
Datasource business UI
        ↓
@yak-ops/yak-ui
        ↓
@base-ui/react
```

Product-only interaction and form state remain inside Datasource。
