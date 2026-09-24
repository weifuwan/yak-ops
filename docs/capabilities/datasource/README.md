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
│   ├── index.tsx
│   ├── type-selector.tsx
│   ├── dynamic-form.tsx
│   ├── form-runtime.tsx
│   ├── driver-manager.tsx
│   ├── jdbc-url-field.tsx
│   └── ssh-tunnel-manager.tsx
├── icons/
└── i18n/

service/datasource/
```

Datasource 页面和 Editor 都采用 feature-locality 结构。

`management / model / plugin / connection / DynamicDataSourceForm` 不再是目录 owner。

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

Dynamic form state is owned by `app/datasource/editor/form-runtime.tsx`。

## Current Capability Map

```text
Datasource Management
Datasource Editor
Connection Test / Connection Normalization
Plugin Configuration
Catalog Browse
```

能力地图不代表每个概念都需要一层目录。

## Shared Rules

- `yak-ops-ui/ARCHITECTURE.md`
- `yak-ops-ui/FRONTEND_RULES.md`
- `yak-ops-ui/apps/web/app/datasource/DATASOURCE_RULES.md`

## Boundary

Datasource is currently the only active Yak Ops product domain.
