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

Frontend Migration Bridge:
- `yak-ops-ui/src/pages/data-source`
- `yak-ops-ui/src/service/datasource`

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

PR1 先建立 package public boundary，不改变 Datasource 用户行为。

现有 `src/pages/data-source` / `src/service/datasource` 是迁移桥；后续 Frontend Datasource Package Refactor 会按 capability 把实现迁入：

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
