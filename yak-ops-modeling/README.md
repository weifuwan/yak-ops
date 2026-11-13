# Yak Ops Modeling Module

The modeling business domain is migrated from [weifuwan/data_modal](https://github.com/weifuwan/data_modal). The module is intentionally isolated from the API and DAO modules so its domain, application services and persistence layer can evolve independently.

## Package boundaries

- `domain`: modeling enums and domain-level types.
- `api/dto`: request DTOs.
- `api/vo`: response VOs and page/tree response types.
- `application/service`: business service interfaces and implementations.
- `infrastructure/persistence/entity`: MyBatis-Plus persistence entities (POs).
- `infrastructure/persistence/mapper`: mapper interfaces.
- `src/main/resources/mapper/modeling`: mapper XML.
- `src/main/resources/sql`: additive database scripts by dialect.

HTTP controllers remain in `yak-ops-api`, following the existing Yak Ops API boundary. The module reuses Yak Ops authentication, project permissions, datasource, MyBatis session factory and Yak-ui components.

## Migrated business scope

### Warehouse planning (first vertical slice)

- Business types and business-type/data-domain relations
- Warehouse layers
- Data domains
- Business processes
- Data marts
- Subject domains

The corresponding original POs, DTOs, VOs, Mapper interfaces, Service interfaces/implementations, controllers and table definitions have been moved into the new package structure. The original business fields and endpoint behavior are retained where possible.

### Subsequent migration batches

- Dimension modeling: dimension tables, fields, version history and reverse modeling.
- Data standards: standard directories, field standards, code standards, units and naming dictionaries.
- Data indicators: atomic, derivative and composite indicators, modifiers and time periods.

## Workspace isolation

All migrated modeling tables must have a non-null `project_code`.

- API routes are rooted at `/projects/{projectCode}/modeling`.
- `ModelingProjectAccessInterceptor` validates read/write access against the active Yak Ops project.
- `ProjectCodeContext` carries the authenticated project code for the duration of the request.
- MyBatis-Plus `TenantLineInnerInterceptor` adds `project_code` predicates to SELECT/UPDATE/DELETE and injects the project code on INSERT for the explicit migrated-table allowlist in `DaoConfiguration`.
- A query against an allowlisted modeling table without an active project context fails closed.
- When a new modeling table is migrated, add it to the DAO tenant allowlist and the schema preflight list in `yak-ops-tools` in the same change.
- The browser's active workspace ID is the Yak Ops project code.

Do not accept a project code from a DTO as an authority. The request path and project permission check are authoritative.

## Database migration

The module owns repeatable, additive scripts:

- `sql/modeling_mysql.sql`
- `sql/modeling_postgresql.sql`
- `sql/modeling_h2.sql`

`yak-ops-tools` depends on this module and executes the modeling patch after the existing Yak Ops baseline has been validated. The scripts only create missing tables and indexes; they do not drop or recreate existing Yak Ops tables.

If a legacy planning table already exists without `project_code`, initialization stops rather than exposing old rows to every workspace. Back up and explicitly assign those legacy rows to a project before enabling the migrated module.

## Frontend

The product is routed under `/data-modeling`. The warehouse planning screens use `@yak-ops/yak-ui` for page headers, buttons, inputs, selects, tables, pagination and modals. Shared CRUD presentation is implemented once and configured per planning entity.

## Migration sequence

1. Warehouse planning (business type, layer, domain, process, mart, subject domain).
2. Dimension modeling and version history.
3. Data standards.
4. Data indicators.
5. Replace remaining legacy interactions with Yak-ui components and remove obsolete compatibility code after verification.
