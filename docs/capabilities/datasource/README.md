# Datasource Domain

Status: Active

Related:
- [PostgreSQL Datasource](./postgresql.md)

Scope:
- Workspace-scoped Datasource CRUD
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

## Workspace Ownership

Datasource is the first Yak Ops Workspace Resource.

```text
Authenticated User
      ↓ membership validated by Boot
X-Workspace-Id
      ↓
WorkspaceContext
      ↓
Datasource
```

Every persisted Datasource has exactly one `workspace_id`. Datasource names are unique inside a Workspace. Resource IDs never bypass Workspace scope: detail, update, delete, paging and saved connection testing all require the active `WorkspaceContext`.

The Datasource HTTP DTO does not accept `workspaceId`; the request Workspace is trusted only after the Workspace interceptor validates membership.

## Backend Flow

```text
DataSourceController
→ WorkspaceContext
→ DataSourceService
→ DataSourceEntityRepository
  or DataSourcePluginRegistry
→ DataSourcePlugin SPI
→ MySQL / Oracle / PostgreSQL JDBC Provider
```

Datasource publishes batch delete and batch saved-connection testing for the management list. Batch delete is transactional; batch connection testing returns one result per requested datasource and continues after individual connection failures.

Datasource also publishes a lightweight connection-property key discovery endpoint:

```text
GET /api/v1/data-source/connection-property-keys?dbType=POSTGRE_SQL
```

The response only contains Provider-recommended advanced-property names. JDBC Providers discover Driver-supported names through `Driver#getPropertyInfo`, merge their known canonical property keys, filter structured connection fields and internal test-only keys, and never open a real database connection for this metadata lookup.

Property keys are suggestions rather than a strict whitelist. Value normalization and validation remain owned by each Provider.

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

The product form uses structured JDBC connection input:

```text
name
dbType
host
port
database
username
password
properties
remark
```

The UI renders a JDBC preview from Host / Port / Database, exposes a fixed username/password identity mode, a fixed no-auth option, an auto driver-version placeholder, and a lightweight provider-neutral Key/Value advanced-properties editor. Create, update and connection-test requests share the same structured `connectionParams` object instead of sending JSON strings. Vendor property names and value semantics are normalized and validated by the selected backend JDBC Provider, not duplicated in the frontend.

The advanced-property Key selector queries the backend Provider for recommended property names. The UI uses a searchable Yak UI Combobox with multiple selection; selecting several recommended keys materializes one independent Key / Value row per key. Existing recommended rows remain searchable/editable, while an explicit custom-property action keeps unknown JDBC properties available. The endpoint remains lightweight metadata only and is never treated as a validation whitelist.

Create uses `DEVELOP` as the default environment. Edit preserves the stored environment.

For PostgreSQL, `database` is the Datasource connection target. `schema` is not a create/edit connection field and must not be appended to the JDBC URL path. When a connection needs a default search path, use the provider-owned advanced property `currentSchema`; Schema discovery and table qualification stay in Catalog.

SSH tunnel UI, dynamic driver-version management, dynamic form schema and runtime plugin install UI are not current product capabilities. JDBC URL generation, driver ownership, provider normalization and connection testing remain owned by the backend JDBC Plugin.

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

Datasource is the active Yak Ops product resource and is always owned by exactly one Workspace.
