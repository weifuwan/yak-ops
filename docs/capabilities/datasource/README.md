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
├── table.tsx
├── toolbar.tsx
├── summary.tsx
├── empty-state.tsx
├── hooks/
├── editor/
│   ├── index.tsx
│   ├── type-selector.tsx
│   ├── dynamic-form.tsx
│   ├── form-runtime.tsx
│   ├── jdbc-url-field.tsx
│   └── ssh-tunnel-manager.tsx
├── icons/
└── i18n/

service/datasource/
├── index.ts
└── types.ts
```

Datasource 页面、Editor 和 Service 都采用 feature-locality 结构。Datasource 列表固定使用原生业务 Table，不再维护 Card/Grid/List 多套展示模式。

`management / model / plugin / connection / DynamicDataSourceForm` 不再是目录 owner。

Datasource Service 当前只拥有真实 UI 消费的 CRUD / Connection / Plugin Config endpoint；Catalog backend capability 未在前端建立浏览入口时不提前镜像 service，运行时插件安装和驱动上传不属于当前产品能力。

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
