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

Plugin Descriptor V4 is runtime-only metadata: canonical type, aliases, API version, capabilities and secret field keys. Frontend labels, sections, validation rules and JDBC URL form linkage are not backend plugin contracts.

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

Datasource publishes read-only Catalog database / Schema / table / column APIs for saved Workspace-scoped datasources. Catalog access does not accept connection credentials; internal runtime connection resolution is never exposed through HTTP.

**Current architecture (Plugin V4):** Provider owns `openConnection()` and MySQL 5/8 isolation / PostgreSQL and Oracle quirks / SSH lifetime. The Datasource Registry injects this ConnectionProvider into `JdbcCatalogFactory`; Catalog, Dialect, Converter and `JdbcTableMetadata` live only in the YakFlow JDBC Connector. A request-scoped Catalog is closed immediately and is never checkpointed.

For exact table discovery, DataSourceService exposes `queryCatalogTable(...)` on the existing HTTP product contract and the internal `queryTableSchema(...)` for Data Sync planning. Both delegate to `yak-flow-connector-jdbc` Catalog. Connector returns native table/column descriptions for UI display and canonical `TableSchema` for engine/validation; Datasource never converts JDBC type codes into a second LogicalType.

Column metadata now preserves JDBC composite-primary-key order through `primaryKeyPosition` / `KEY_SEQ` in addition to the existing `primaryKey` membership flag. See [DataSourceController](../../../yak-ops-boot/src/main/java/io/yak/ops/boot/controller/datasource/v1/DataSourceController.java) and [Datasource Rules](../../../yak-ops-business/yak-ops-business-datasource/DATASOURCE_RULES.md) for the backend boundary. Summary and arbitrary SQL execution APIs remain outside this capability.

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
- `form.tsx`: create, edit, connection test and frequent datasource-type presentation.
- `service/datasource`: CRUD, batch operations and connection-test HTTP Contract.
- `service/preference`: user-scoped `DATASOURCE_CREATE_TYPE` usage signals used by the create wizard.

## Create Type Selection

Create step one renders up to three frequent datasource types above the complete supported datasource list.

Ranking:

```text
DATASOURCE_CREATE_TYPE preferences
→ useCount DESC
→ lastUsedTime DESC
→ current supported datasource registry
→ Top 3
```

When fewer than three user usage records map to currently supported datasource types, the remaining slots are filled from the current product type order. Selecting a datasource type records one usage event asynchronously and never blocks navigation into connection configuration. This preference is user-scoped and does not use the active Workspace as an ownership key.

## Connection Form

The product form uses fixed Provider-owned connection modes:

```text
MySQL / PostgreSQL
→ host / port / database / username / password / properties

Oracle
→ jdbcUrl / username / password
```

MySQL and PostgreSQL render a JDBC preview from Host / Port / Database and expose the lightweight provider-neutral Key/Value advanced-properties editor. Oracle accepts the full native `jdbc:oracle:` URL directly, does not split SID / Service Name / RAC topology into product fields, and does not expose the advanced-properties editor. All create, update and connection-test requests still share the same `connectionParams` object instead of sending JSON strings.

Only MySQL exposes a Driver version selector. `AUTO` and `MYSQL_8` use the built-in Connector/J 8 runtime; `MYSQL_5` uses the built-in Connector/J 5.1 runtime. Both runtimes are loaded from `jdbc-drivers-builtin/mysql/{8,5}` through isolated ClassLoaders and are never added to Spring Boot `loader.path`. PostgreSQL and Oracle do not render or submit `driverId`.

For MySQL and PostgreSQL, the advanced-property Key selector queries the backend Provider for recommended property names. The UI uses a searchable Yak UI Combobox with multiple selection; selecting several recommended keys materializes one independent Key / Value row per key. Existing recommended rows remain searchable/editable, while an explicit custom-property action keeps unknown JDBC properties available. Oracle skips this discovery path and its Provider returns no advanced-property candidates.

Create uses `DEVELOP` as the default environment. Edit preserves the stored environment.

For PostgreSQL, `database` is the Datasource connection target. `schema` is not a create/edit connection field and must not be appended to the JDBC URL path. When a connection needs a default search path, use the provider-owned advanced property `currentSchema`; Schema discovery and table qualification stay in Catalog.

SSH tunnel UI, arbitrary driver upload/installation, dynamic form schema and runtime plugin install UI are not current product capabilities. MySQL has a fixed built-in 5.x / 8.x driver selector; JDBC URL generation, driver ownership, provider normalization and connection testing remain owned by the backend JDBC Plugin.

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
