# Yak Ops Modeling Module

This module contains the data modeling business domain migrated from `data_modal`.

## Responsibilities

- Data warehouse planning: business categories, warehouse layers, data domains, business processes, data marts and subject domains.
- Model design: source, dimension, detail, summary and application models, fields, partitions and model versions.
- Data standards: field standards, code standards, units and naming dictionaries.
- Data indicators: atomic, derived and composite indicators, modifiers and time periods.

## Module boundaries

- Business services, domain objects, persistence entities and mapper interfaces belong in this module.
- HTTP controllers remain in `yak-ops-api`, following the existing Yak Ops API boundary.
- Shared authentication, workspace/project permissions, data-source connections and metadata discovery must reuse Yak Ops services.
- The module does not introduce a second datasource, authentication stack, MyBatis session factory or UI component library.

## Workspace isolation rules

Every modeling business record must be scoped by the active Yak Ops project:

1. Every project-owned table must contain a non-null `project_code`.
2. Read and write queries must always include `project_code`; never query modeling records by business ID alone.
3. Unique constraints must include `project_code` where uniqueness is workspace-local (for example, `project_code + table_name`).
4. API read operations must validate access with `ProjectService.checkProjectAndAuthThrowException`.
5. API write operations must validate write access with `ProjectService.checkHasProjectWritePermissionThrowException`.
6. The browser's active workspace is the Project code, not the Project database primary key.

## Persistence and schema rules

- Modeling entities and mapper interfaces live under `io.yak.ops.modeling.infrastructure.persistence`.
- Mapper XML files live under `src/main/resources/mapper/modeling`.
- Modeling DDL and incremental patches live in this module's `src/main/resources/sql`, separated by database dialect.
- Do not copy the original destructive `cockpit.sql` baseline into Yak Ops.
- Additive schema changes must be idempotent and must not drop or recreate existing Yak Ops tables.
- MySQL and PostgreSQL-compatible scripts must be maintained separately. OpenGauss compatibility must be verified before claiming support.

## Migration phases

Phase 1 establishes the Maven module, Spring integration, workspace-aware API bootstrap and product navigation. Domain tables and CRUD features are intentionally introduced with their corresponding business capabilities in later phases.
