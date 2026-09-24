# Datasource Domain

Status: Review

Scope:
- Datasource registration and lifecycle
- Connection handling
- Catalog browsing
- Datasource plugin metadata
- SQL execution and execution audit
- Datasource frontend

## Current Owners

Backend:
- `yak-ops-business/yak-ops-business-datasource`

Plugin:
- `yak-ops-plugins/yak-ops-plugin-datasource`

Frontend Public Owner:
- `yak-ops-ui/packages/datasource`

Frontend Capabilities:
- `yak-ops-ui/packages/datasource/src/management`
- `yak-ops-ui/packages/datasource/src/editor`
- `yak-ops-ui/packages/datasource/src/connection`
- `yak-ops-ui/packages/datasource/src/plugin`
- `yak-ops-ui/packages/datasource/src/model`
- `yak-ops-ui/packages/datasource/src/api`

Data:
- `yak-ops-dao/src/main/resources/db/migration/yak-ops`
- `yak-ops-business/yak-ops-business-datasource/src/main/resources/mapper`

## Frontend Dependency

```text
apps/web
   ↓
@yak-ops/datasource
   ↓
@yak-ops/yak-ui
```

Datasource frontend implementation is fully owned by `yak-ops-ui/packages/datasource`.

`src/pages/data-source` and `src/service/datasource` have been removed. App enters Datasource only through `@yak-ops/datasource`.

Ant Design / `@ant-design/icons` have been removed from the frontend. Datasource uses `@yak-ops/yak-ui`, Lucide icons and native browser capabilities. Dynamic form state is owned by `packages/datasource/src/editor/formRuntime.tsx`.

Internal ownership:

```text
management
editor
connection
plugin
model
api
```

## Current Capability Map

```text
Datasource Management
Connection Test / Connection Normalization
Plugin Configuration
Catalog Browse
SQL Execution
SQL Execution Audit / Observability
```

这只是当前代码能力地图，不代表每个 Contract 已经确认。

## Shared Rules

- root `ARCHITECTURE.md`
- root `JAVA_RULES.md`
- `DATASOURCE_RULES.md`
- `FLYWAY_RULES.md` when schema changes
- `PLUGIN_RULES.md` when plugin behavior changes
- `yak-ops-ui/ARCHITECTURE.md`
- `yak-ops-ui/packages/datasource/DATASOURCE_UI_RULES.md`

## Development Order

```text
choose one capability
→ inspect current code
→ write the capability contract
→ review contract
→ implement the gap
→ review
→ explicit verification
→ mark Done
```

## Boundary

Datasource is currently the only active Yak Ops product domain.

Removed domains are not dependencies, reference architectures or future requirements unless explicitly reintroduced.
