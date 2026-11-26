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

Each modeling table has one independent PO, Mapper, DAO interface and DAO implementation. The data-standard and indicator pages each expose a dedicated typed service contract and implementation. Shared CRUD mechanics live in a small abstract service; resource-specific validation, relations, versioning and nested records remain in their own service. `ModelingAssetDao` is used only as the persistence facade, not as a string-dispatched application service.

HTTP controllers remain in `yak-ops-api`, following the existing Yak Ops API boundary. Each standards/indicator page has its own controller and REST resource path under `/projects/{projectCode}/modeling`: `standard-directories`, `field-standards`, `code-standards`, `measurement-units`, `naming-dictionaries`, `atom-indicators`, `derived-indicators`, `composite-indicators`, `modifiers` and `time-periods`. The modules reuse Yak Ops authentication, project permissions, datasource and MyBatis session factory.

## REST endpoints

All endpoints below are prefixed with `/projects/{projectCode}/modeling`.

| Page / capability | Resource path | Controller | Service |
|---|---|---|---|
| Standard directories | `/standard-directories` | `StandardDirectoryController` | `StandardDirectoryService` |
| Field standards | `/field-standards` | `FieldStandardController` | `FieldStandardService` |
| Standard codes | `/code-standards` | `CodeStandardController` | `CodeStandardService` |
| Measurement units | `/measurement-units` | `MeasurementUnitController` | `MeasurementUnitService` |
| Naming dictionaries | `/naming-dictionaries` | `NamingDictionaryController` | `NamingDictionaryService` |
| Atomic indicators | `/atom-indicators` | `AtomIndicatorController` | `AtomIndicatorService` |
| Derived indicators | `/derived-indicators` | `DerivedIndicatorController` | `DerivedIndicatorService` |
| Composite indicators | `/composite-indicators` | `CompositeIndicatorController` | `CompositeIndicatorService` |
| Modifiers | `/modifiers` | `ModifierController` | `ModifierService` |
| Time periods and categories | `/time-periods` | `TimePeriodController` | `TimePeriodService` |

Each resource uses typed `page`, `list`, `getById`, `create`, `update` and `delete` operations. Versioned indicator resources additionally expose `POST /{id}/submit`, `GET /{id}/versions` and `POST /{id}/rollback?versionNumber={versionNumber}`. Standard-code details are nested under `/code-standards/{id}/details`; time-period categories are nested under `/time-periods/categories`.

## Migrated business scope

### Warehouse planning

- Business types and business-type/data-domain relations
- Warehouse layers
- Data domains
- Business processes
- Data marts
- Subject domains
- Existing dimension table and field metadata
- Doris publish history with immutable resolved physical-configuration snapshots; UNIQUE KEY fields derive from logical field `primaryFlag`
- Dimension tree grouping by business type, data domain, mart and subject domain

### Migrated modeling assets

- Data standards: standard directories, field standards, code standards, units and naming dictionaries.
- Data indicators: atomic, derivative and composite indicators, modifiers and time periods.
- Existing version metadata and dimension metadata persistence.

Core forward data modeling and reverse engineering remain outside this migration scope.

## Workspace isolation

All 23 current modeling tables use the `t_yak_ops_` prefix and must have a non-null `project_code`.

- API routes are rooted at `/projects/{projectCode}/modeling`.
- `ModelingProjectAccessInterceptor` validates read/write access against the active Yak Ops project.
- `ProjectCodeContext` carries the authenticated project code for the duration of the request.
- MyBatis-Plus `TenantLineInnerInterceptor` adds `project_code` predicates to SELECT/UPDATE/DELETE and injects the project code on INSERT for the explicit migrated-table allowlist in `DaoConfiguration`.
- A query against an allowlisted modeling table without an active project context fails closed.
- When another modeling table is added, update the dialect scripts, DAO tenant allowlist and schema preflight list in the same change.
- Never accept a project code from a DTO as an authority. The request path and project permission check are authoritative.

## Database schema and reset

The module owns three complete, additive schema scripts:

- `sql/modeling_mysql.sql`
- `sql/modeling_postgresql.sql`
- `sql/modeling_h2.sql`

Each script defines the same 23 current tables with aligned table and index definitions, with the same logical column set. Table names, table comments and index names are aligned across dialects. All table and index DDL is kept before the initialization DML. The scripts only create missing tables and indexes; they do not drop or recreate existing tables.

The DML section normalizes legacy warehouse-layer codes, initializes atomic-indicator `dw_type`, and seeds project-scoped defaults for warehouse layers, unit directories and time-period categories/assets.

For a modeling-only reset, use the matching `sql/modeling_drop_<dialect>.sql` script, then run the matching complete `sql/modeling_<dialect>.sql` script. The reset scripts remove the 23 current logical modeling tables and also clean up the three retired Doris configuration tables under both their `t_yak_ops_` and former `t_cockpit_` names. They preserve Yak Ops base tables such as projects, users, roles and permissions. They delete modeling data for every project and must be run manually after a backup; they are never part of application startup. See `src/main/resources/sql/README.md` for the exact rebuild procedure and verification checks.

All four indicator-asset tables (modifier, atomic, derivative and composite) use `dw_type` directly and do not reference concrete warehouse-layer IDs. The dimension-model table intentionally retains `dwh_id`. The complete schema includes the corresponding classification indexes; the retired one-time patch scripts are not part of a clean rebuild.

Before applying the normalized schema, `SchemaDao.ensureModelingSchema()` checks for former `t_cockpit_` table names. For the current 23 logical tables, when only a legacy table exists, it renames it in place and normalizes its index names, preserving rows. If both old and new logical table names exist, initialization stops and requires explicit data reconciliation. A dedicated Doris configuration migration is the exception: it first converts legacy Doris Key rows into `primary_flag = true` and `can_null = false`, ensures the legacy `physical_config` and current `physical_schema` columns on publish history, then drops the three obsolete mutable Doris configuration tables. Historical V3 version snapshots are also translated into `primaryFlag` when rolled back.

If a legacy table does not contain `project_code`, initialization stops rather than exposing unscoped rows to every workspace. Back up and explicitly assign those rows to a project before enabling the migrated module.

## Frontend

The product is routed under `/kimball`. The warehouse planning screens use `@yak-ops/yak-ui` for page headers, buttons, inputs, selects, tables, pagination and modals. Shared CRUD presentation is implemented once and configured per planning entity.

## Migration sequence

1. Warehouse planning (business type, layer, domain, process, mart and subject domain).
2. Existing dimension metadata and tree grouping.
3. Data standards.
4. Data indicators.
5. Core forward data modeling and reverse engineering (deferred).
