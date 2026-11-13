# Yak Ops Modeling Module

The modeling business domain is migrated from [weifuwan/data_modal](https://github.com/weifuwan/data_modal). The module keeps modeling-specific DTOs, VOs, application services and domain orchestration separate from the shared persistence module.

## Package boundaries

- `domain`: modeling domain types and grouping rules.
- `api/dto`: request DTOs.
- `api/vo`: response VOs and tree/page response types.
- `application/service`: modeling service interfaces and implementations, including business orchestration and transaction boundaries.
- `config/ModelingConfiguration`: registers the Modeling application services only.
- `src/main/resources/sql`: complete additive schema scripts for each supported database dialect.

All persistence code is owned by `yak-ops-dao`, following the repository-level `AGENTS.md` rules:

- Entities: `yak-ops-dao/src/main/java/io/yak/ops/dao/entity`
- Modeling mappers: `yak-ops-dao/src/main/java/io/yak/ops/dao/mapper`
- DAO interfaces: `yak-ops-dao/src/main/java/io/yak/ops/dao/repository/modeling`
- DAO implementations: `yak-ops-dao/src/main/java/io/yak/ops/dao/repository/impl/modeling`
- Mapper XML: `yak-ops-dao/src/main/resources/io/yak/ops/dao/mapper`

Each modeling table has one independent PO, Mapper, DAO interface and DAO implementation. The 13 data-standard and indicator asset entities are no longer nested in a single PO container. The dynamic asset service uses a dedicated `ModelingAssetDao` facade, keeping mapper execution inside the DAO layer.

HTTP controllers remain in `yak-ops-api`, following the existing Yak Ops API boundary. The modules reuse Yak Ops authentication, project permissions, datasource, MyBatis session factory and Yak UI components.

## Migrated business scope

### Warehouse planning

- Business types and business-type/data-domain relations
- Warehouse layers
- Data domains
- Business processes
- Data marts
- Subject domains
- Existing dimension table and field metadata
- Dimension tree grouping by business type, data domain, mart and subject domain

### Migrated modeling assets

- Data standards: standard directories, field standards, code standards, units and naming dictionaries.
- Data indicators: atomic, derivative and composite indicators, modifiers and time periods.
- Existing version metadata and dimension metadata persistence.

Core forward data modeling and reverse engineering remain outside this migration scope.

## Workspace isolation

All 22 modeling tables use the `t_yak_ops_` prefix and must have a non-null `project_code`.

- API routes are rooted at `/projects/{projectCode}/modeling`.
- `ModelingProjectAccessInterceptor` validates read/write access against the active Yak Ops project.
- `ProjectCodeContext` carries the authenticated project code for the duration of the request.
- MyBatis-Plus `TenantLineInnerInterceptor` adds `project_code` predicates to SELECT/UPDATE/DELETE and injects the project code on INSERT for the explicit migrated-table allowlist in `DaoConfiguration`.
- A query against an allowlisted modeling table without an active project context fails closed.
- When another modeling table is added, update the dialect scripts, DAO tenant allowlist and schema preflight list in the same change.
- Never accept a project code from a DTO as an authority. The request path and project permission check are authoritative.

## Database schema and migration

The module owns three complete, additive schema scripts:

- `sql/modeling_mysql.sql`
- `sql/modeling_postgresql.sql`
- `sql/modeling_h2.sql`

Each script defines the same 22 tables and 54 indexes, with the same logical column set. Table names, table comments and index names are aligned across dialects. The scripts only create missing tables and indexes; they do not drop or recreate existing tables.

Before applying the normalized schema, `SchemaDao.ensureModelingSchema()` checks for former `t_cockpit_` table names. When only a legacy table exists, it renames it in place and normalizes its index names, preserving rows. If both old and new table names exist, initialization stops and requires explicit data reconciliation. It never drops or merges either table automatically.

If a legacy table does not contain `project_code`, initialization stops rather than exposing unscoped rows to every workspace. Back up and explicitly assign those rows to a project before enabling the migrated module.

## Frontend

The product is routed under `/data-modeling`. The warehouse planning screens use `@yak-ops/yak-ui` for page headers, buttons, inputs, selects, tables, pagination and modals. Shared CRUD presentation is implemented once and configured per planning entity.

## Migration sequence

1. Warehouse planning (business type, layer, domain, process, mart and subject domain).
2. Existing dimension metadata and tree grouping.
3. Data standards.
4. Data indicators.
5. Core forward data modeling and reverse engineering (deferred).
