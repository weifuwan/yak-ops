# Datasource Domain

Status: Active

Scope:
- Datasource CRUD
- Connection testing
- JDBC plugin runtime
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

## Product Boundary

Current Datasource product is intentionally small:

```text
MySQL / Oracle / PostgreSQL
        ↓
Filter + Table + Pagination
        ↓
Create / Edit / Delete / Batch Delete
        ↓
Test Connection / Batch Test Connection
```

Backend JDBC Plugin stays extensible, but the frontend is not a plugin platform.

Plugin Descriptor V3 is runtime-only metadata: canonical type, aliases, API version, capabilities and secret field keys. Frontend labels, sections, validation rules and JDBC URL form linkage are not backend plugin contracts.

## Backend Flow

```text
DataSourceController
→ DataSourceService
→ DataSourceEntityRepository
  or DataSourcePluginRegistry
→ DataSourcePlugin SPI
→ MySQL / Oracle / PostgreSQL JDBC Provider
```

Datasource publishes batch delete and batch saved-connection testing for the management list. Batch delete is transactional; batch connection testing returns one result per requested datasource and continues after individual connection failures.

Datasource does not publish Catalog or Summary product APIs.

## Frontend Structure

```text
app/datasource/
├── index.tsx
├── table.tsx
├── form.tsx
├── constants.ts
├── types.ts
├── icons/
└── i18n/

service/datasource/
├── index.ts
└── types.ts
```

Responsibilities:

- `index.tsx`: filters, paging, list loading, controlled selection, batch operations, delete confirmation and drawer state.
- `table.tsx`: table rendering, row selection, row actions and batch footer composition.
- `form.tsx`: create, edit and connection test.
- `service/datasource`: CRUD, batch operations and connection-test HTTP Contract.

## Connection Form

The product form only exposes:

```text
name
dbType
jdbcUrl
username
password
remark
```

Create uses `DEVELOP` as the default environment. Edit preserves the stored environment.

Host/Port/Database linkage, SSH tunnel UI, driver configuration, JDBC properties editor, dynamic form schema and runtime plugin install UI are not current product capabilities.

Provider-specific connection behavior remains owned by the backend JDBC Plugin.

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

## Shared Rules

- `yak-ops-ui/ARCHITECTURE.md`
- `yak-ops-ui/FRONTEND_RULES.md`
- `yak-ops-ui/apps/web/app/datasource/DATASOURCE_RULES.md`

## Boundary

Datasource is currently the only active Yak Ops product domain.
