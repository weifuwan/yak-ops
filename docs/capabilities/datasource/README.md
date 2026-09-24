# Datasource Domain

Status: Review

Scope:
- Datasource registration and lifecycle
- Connection handling
- Catalog browsing
- Datasource plugin metadata
- Datasource frontend

## Current Owners

Backend:
- `yak-ops-business/yak-ops-business-datasource`

Plugin:
- `yak-ops-plugins/yak-ops-plugin-datasource`

Frontend Product Owner:
- `yak-ops-ui/apps/web/app/datasource`

Frontend Service Owner:
- `yak-ops-ui/apps/web/service/datasource`

Frontend Capabilities:
- `app/datasource/management`
- `app/datasource/editor`
- `app/datasource/connection`
- `app/datasource/plugin`
- `app/datasource/model`
- `service/datasource`

Data:
- `yak-ops-dao/src/main/resources/db/migration/yak-ops`
- `yak-ops-business/yak-ops-business-datasource/src/main/resources/mapper`

## Frontend Dependency

```text
app/router
   ↓
app/datasource
   ↓
service/datasource
   ↓
service/http

app/datasource
   ↓
@yak-ops/yak-ui
```

Datasource is a Web App Domain, not an npm workspace package.

`packages/datasource` and `@yak-ops/datasource` have been removed.

Dynamic form state remains owned by `app/datasource/editor/formRuntime.tsx`.

## Current Capability Map

```text
Datasource Management
Connection Test / Connection Normalization
Plugin Configuration
Catalog Browse
```

这只是当前代码能力地图，不代表每个 Contract 已经确认。

## Shared Rules

- root `ARCHITECTURE.md`
- root `JAVA_RULES.md`
- `DATASOURCE_RULES.md`
- `FLYWAY_RULES.md` when schema changes
- `PLUGIN_RULES.md` when plugin behavior changes
- `yak-ops-ui/ARCHITECTURE.md`
- `yak-ops-ui/apps/web/app/datasource/DATASOURCE_RULES.md`

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
