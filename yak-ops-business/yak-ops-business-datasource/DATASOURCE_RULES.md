# Datasource Rules

Scope:
- `yak-ops-business/yak-ops-business-datasource/**`

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- `/yak-ops-business/BUSINESS_RULES.md`
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

## Business Entry

Datasource exposes three stable Business contracts:

```text
DataSourceBusiness
DataSourceCatalogBusiness
DataSourcePluginBusiness
```

Implementation follows the common Business rule:

```text
XxxBusiness
→ impl/XxxBusinessImpl
```

Boot only depends on these Business interfaces. `Manager / Reader / Registry / Tester` are not Controller-facing business entry points.

## Package Ownership

```text
datasource   datasource lifecycle, reads and connection testing
catalog      database / schema / table / column metadata
plugin       plugin discovery, parsing, masking and Catalog creation
config       capability-local properties / conditions
exception    datasource business errors
```

Catalog cache, diagnostics and matching logic may remain internal collaborators when they own real state or mechanism.

Do not recreate `domain`, `gateway`, `execution`, `query` or business `repository` packages unless a new capability contract proves a real boundary.

## HTTP Boundary

Datasource HTTP Controller、ControllerAdvice 统一由 `yak-ops-boot` 持有。

Controller 只负责 HTTP mapping、`@Valid` 和统一 Result 包装。DTO parsing、业务校验、Entity → VO、Plugin descriptor → VO 等业务输出转换归 Business。

数据源分页请求统一由 `DataSourceQueryDTO extends PageQueryDTO` 提供 `pageNo / pageSize / sorts` Contract。当前自定义 `sorts` 在 Repository 排序白名单落地前必须明确拒绝，禁止静默忽略；默认分页排序保持 `updateTime DESC, id DESC` 保证稳定翻页。

本模块只提供 Datasource capability，不创建 `controller` package，也不依赖 Boot。

## Business Rules

Must:
- datasource mutation、read 和 connection test 由 `DataSourceBusiness` 统一持有。
- Catalog 元数据能力由 `DataSourceCatalogBusiness` 持有。
- Plugin 配置与可用性入口由 `DataSourcePluginBusiness` 持有。
- business validation stays close to Datasource behavior, not Controller.
- DAO Entity / Repository 只出现在 BusinessImpl 内部。
- Business 对 Boot 返回公共 VO，不返回 `DataSourceEntity`、`DataSourceSummaryRow` 或 Repository Query。
- plugin behavior enters through stable Plugin SPI inside Business implementation.
- datasource `db_type` is the canonical Provider-owned plugin type string.
- incoming plugin type / alias resolution goes through `DataSourcePluginBusiness`; Business does not own a database-type enum.
- Catalog is metadata-only: database, schema, table and column discovery.
- secret handling must never leak raw credentials into logs, errors or response objects.

Must Not:
- add Domain objects that mirror `DataSourceEntity`.
- add Gateway / Adapter wrappers that only forward the Plugin SPI.
- add Business Repository wrappers that only map DAO Entity to another model.
- add SQL execution, SQL preview, SQL template, SQL variable resolution or SQL audit behavior.
- use Manager / Reader / Registry / Tester as Boot-facing business APIs.
- access concrete plugin implementations from business code.
- maintain a Common/Business enum or switch listing all supported datasource providers.
- create application-level DataSource / transaction manager / SqlSessionFactory / MyBatis-Plus plugin configuration in this module.

## Persistence

```text
DataSourceBusinessImpl
→ DataSourceEntityRepository
→ DataSourceMapper / DataSourceEntity
→ MyBatis / SQL
```

- `yak-ops-dao` owns Entity / Mapper / Repository implementation and Mapper XML.
- Business does not duplicate DAO persistence models.
- Entity / DAO Model must be converted before leaving BusinessImpl.
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
