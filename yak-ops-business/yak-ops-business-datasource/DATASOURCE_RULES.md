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
- Datasource CRUD
- Datasource paging / detail
- Datasource connection testing
- Internal Datasource Plugin discovery, connection parsing and secret handling

Does Not Own:
- Catalog HTTP / Business capability
- SQL execution
- SQL audit / observability
- duplicate Domain models around DAO Entity
- Gateway / Adapter layers around the stable Plugin SPI

## Service Entry

Datasource exposes exactly one stable Service Layer contract:

```text
DataSourceService
→ impl/DataSourceServiceImpl
```

Boot only depends on `DataSourceService`.

`DataSourcePluginRegistry`、`DataSourceSecretCodec` 等只属于 Service 内部机制，不是第二套业务入口，不得被 Controller 注入。

禁止重新创建：

```text
DataSourceBusiness
DataSourceCatalogBusiness
DataSourcePluginBusiness
```

除非新的产品 Capability Contract 明确证明需要独立业务边界。

## Package Ownership

```text
datasource   datasource lifecycle, reads and connection testing
plugin       internal plugin discovery, parsing and secret handling
config       capability-local properties / conditions
exception    datasource business errors
security     secret-safe text helpers
```

Do not recreate `catalog / domain / gateway / execution / query` business packages unless a new capability contract proves a real product boundary.

## HTTP Boundary

Datasource HTTP Controller、ControllerAdvice 统一由 `yak-ops-boot` 持有。

Controller 只负责 HTTP mapping、`@Valid` 和统一 Result 包装。DTO parsing、业务校验、Entity → VO 等业务输出转换归 `DataSourceServiceImpl`。

数据源分页请求统一由 `DataSourceQueryDTO extends PageQueryDTO` 提供 `pageNo / pageSize / sorts` Contract。当前自定义 `sorts` 在 Repository 排序白名单落地前必须明确拒绝，禁止静默忽略；默认分页排序保持 `updateTime DESC, id DESC` 保证稳定翻页。

当前 Datasource 管理产品面只发布分页、详情、增删改和连接测试。

当前产品不发布 Catalog、Plugin Config HTTP schema、运行时插件安装或 Driver Upload API。

本模块只提供 Datasource capability，不创建 `controller` package，也不依赖 Boot。

## JDBC Plugin Boundary

后端 JDBC Plugin 体系必须保留。

Runtime path:

```text
DataSourceServiceImpl
→ DataSourcePluginRegistry
→ DataSourcePlugin SPI
→ MySqlDataSourcePlugin / OracleDataSourcePlugin / PostgreSqlDataSourcePlugin
→ JDBC Driver
```

Must:
- Provider 继续通过 `ServiceLoader` 发现。
- Provider 自己拥有 canonical type、aliases、JDBC URL / driver quirks、参数 Normalize 和 Connection Test。
- Service 不维护数据库类型 enum 或 provider switch。
- `yak-ops-plugin-datasource-all` 继续负责运行时聚合 built-in JDBC providers。
- 当前产品基线只打包 MySQL、Oracle、PostgreSQL。
- secret handling must never leak raw credentials into logs, errors or response objects.

Must Not:
- Controller 直接访问 Plugin Registry / Plugin SPI。
- Service 直接访问具体 Provider implementation。
- 为 Plugin 再增加一层 Business / Manager / Adapter。
- 通过后端 Plugin descriptor 动态驱动前端表单。

## Persistence

```text
DataSourceServiceImpl
→ DataSourceEntityRepository
→ DataSourceMapper / DataSourceEntity
→ MyBatis / SQL
```

- `yak-ops-dao` owns Entity / Mapper / Repository implementation and Mapper XML.
- Service does not duplicate DAO persistence models.
- Entity / DAO Model must be converted before leaving `DataSourceServiceImpl`.
- Schema evolution is owned by `yak-ops-dao`.
- Final application MyBatis runtime assembly is owned by `yak-ops-boot`.
