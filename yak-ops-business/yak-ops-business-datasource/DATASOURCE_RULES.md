# Datasource Rules

Scope:
- `yak-ops-business/yak-ops-business-datasource/**`

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- HTTP contract 变化加载 `/CONTROLLER_RULES.md`
- Schema 变化加载 `/yak-ops-dao/FLYWAY_RULES.md`
- Plugin 变化加载 `/yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN_RULES.md`

Owns:
- Datasource management
- Datasource connection testing
- Catalog metadata browsing
- Datasource plugin discovery and connection handling

Does Not Own:
- SQL execution
- SQL audit / observability
- duplicate Domain models around DAO Entity
- Gateway / Adapter layers around the stable Plugin SPI

## Package Ownership

```text
management   datasource lifecycle, reads and connection testing
catalog      database / schema / table / column metadata
plugin       plugin discovery, parsing, masking and Catalog creation
config       capability-local properties / conditions
security     sensitive text masking
exception    datasource business errors
```

Do not recreate `domain`, `gateway`, `execution`, `query` or business `repository` packages unless a new capability contract proves a real boundary.

## HTTP Boundary

Datasource HTTP Controller、ControllerAdvice 和 Controller-only converter 统一由 `yak-ops-boot` 持有。

本模块只提供 Datasource capability，不创建 `controller` package，也不依赖 Boot。

## Business Rules

Must:
- datasource mutation and read behavior has one clear management owner.
- business validation stays close to Datasource behavior, not Controller.
- current datasource persistence uses DAO-owned Entity / Repository directly.
- plugin behavior enters through `DataSourcePluginRegistry` and the stable Plugin SPI.
- Catalog is metadata-only: database, schema, table and column discovery.
- secret handling must never leak raw credentials into logs, errors or response objects.

Must Not:
- add Domain objects that mirror `DataSourceEntity`.
- add Gateway / Adapter wrappers that only forward the Plugin SPI.
- add Business Repository wrappers that only map DAO Entity to another model.
- add SQL execution, SQL preview, SQL template, SQL variable resolution or SQL audit behavior.
- add Manager / Reader / Adapter only because neighboring code has one.
- access concrete plugin implementations from business code.
- create application-level DataSource / transaction manager / SqlSessionFactory / MyBatis-Plus plugin configuration in this module.

## Persistence

```text
Datasource management
→ DataSourceEntityRepository
→ DataSourceMapper / DataSourceEntity
→ MyBatis / SQL
```

- `yak-ops-dao` owns Entity / Mapper / Repository implementation and Mapper XML.
- Business does not duplicate DAO persistence models.
- Schema evolution is owned by `yak-ops-dao`.
- Final application MyBatis runtime assembly is owned by `yak-ops-boot`.

## Catalog

Catalog only describes datasource metadata:

```text
database
→ schema
→ table / view / collection / index
→ column / field
```

Preview, count, SQL describe, SQL template generation and SQL variable resolution are not Catalog responsibilities.
