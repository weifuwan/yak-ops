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

Frontend Structure:

```text
app/datasource/
├── index.tsx
├── card.tsx
├── toolbar.tsx
├── summary.tsx
├── empty-state.tsx
├── hooks/
├── editor/
├── connection/
├── icons/
└── i18n/

service/datasource/
```

Datasource 页面层已从 capability layering 收口为 feature-locality 结构。

`management / model / plugin` 不再是目录 owner。

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

Dynamic form state remains owned by `app/datasource/editor/formRuntime.tsx`。

## Current Capability Map

```text
Datasource Management
Connection Test / Connection Normalization
Plugin Configuration
Catalog Browse
```

这只是当前代码能力地图，不代表每个概念都需要一层目录。

## Shared Rules

- root `ARCHITECTURE.md`
- root `JAVA_RULES.md`
- `DATASOURCE_RULES.md`
- `FLYWAY_RULES.md` when schema changes
- `PLUGIN_RULES.md` when plugin behavior changes
- `yak-ops-ui/ARCHITECTURE.md`
- `yak-ops-ui/apps/web/app/datasource/DATASOURCE_RULES.md`

## Boundary

Datasource is currently the only active Yak Ops product domain.

Removed domains are not dependencies, reference architectures or future requirements unless explicitly reintroduced.
