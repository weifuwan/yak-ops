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
plugin       internal plugin discovery, parsing and datasource secret handling
config       capability-local runtime properties
exception    datasource business errors
```

Do not recreate `catalog / domain / gateway / execution / query` business packages unless a new capability contract proves a real product boundary.

## Configuration Boundary

- Datasource 是当前产品基线能力，不维护 `yak.datasource.enabled` 开关，也不把 `@ConditionalOnProperty` 扩散到 Service / Plugin / Controller。
- `DataSourceProperties` 只承载真实可调运行参数；单个标量配置不得为了层级形式再创建 nested properties type。
- 当前连接测试超时统一由 `yak.datasource.connection-test-timeout-seconds` 提供。
- 应用自身数据库仍由 `spring.datasource` / `YAK_DATABASE_*` 持有；禁止重新引入容易混淆的 `YAK_DATASOURCE_URL / USERNAME / PASSWORD` 兼容别名。

## JSON / Secret Boundary

- JSON parser / writer 统一复用 Common `JsonUtils`，本模块不得注入或创建独立 `ObjectMapper`。
- 通用敏感文本遮罩统一复用 Common `SensitiveUtils`。
- Datasource 特有的 secret key 识别、JSON 递归遮罩和编辑态 secret merge 继续由 `DataSourceSecretCodec` 持有，不下沉 Common。
- Plugin Registry 只负责插件发现和路由，不重复实现 JSON 基础设施。

## Constant Boundary

- Datasource 不为了 HTTP 路径创建 Business 领域常量；HTTP ownership 属于 Boot。
- 只有一个调用方使用的固定值留在调用类，不创建 `DataSourceConstants`。
- Boot 的 Datasource API 根路径直接基于 `CommonConstants.API_PREFIX` 组合，禁止在 Common 建立 Datasource 常量中转层。
- 只有多个真实 Datasource 调用方共享、且不可配置的领域值，才允许在 Datasource owner 内创建领域常量。
- Plugin API version、provider metadata 等由 Plugin / SPI 自己持有，不搬进 Business 常量类。

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
