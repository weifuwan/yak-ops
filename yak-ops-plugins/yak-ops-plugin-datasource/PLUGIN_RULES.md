# Datasource Plugin Rules

Scope:
- `yak-ops-plugins/yak-ops-plugin-datasource/**`

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`

Owns:
- stable Datasource provider contract
- provider-specific connection behavior
- provider-specific catalog behavior
- provider-specific SQL execution adaptation
- built-in plugin assembly

## Module Ownership

```text
yak-ops-plugin-datasource-api
→ stable provider-neutral contracts

yak-ops-plugin-datasource-jdbc
→ shared JDBC implementation + built-in MySQL / Oracle / PostgreSQL providers

yak-ops-plugin-datasource-all
→ runtime aggregation only
```

## API Rules

`yak-ops-plugin-datasource-api` must:
- expose production API types only under `io.yak.ops.plugin.datasource.api` and its owner subpackages.
- keep Plugin contracts under `api.plugin`, Catalog contracts/models under `api.catalog`, finite domain values under `api.enums`, and Plugin exceptions under `api.exception`.
- keep independently meaningful API enums as top-level types; public nested enums/classes/records/interfaces are forbidden.
- prefer records for immutable Catalog query/path/metadata carriers when validation can stay explicit.
- never recreate the legacy `io.yak.ops.spi.datasource` namespace.
- stay provider-neutral.
- expose stable capability and connection contracts.
- avoid Datasource Service Layer implementation types.
- avoid concrete driver classes.
- keep default methods only when they preserve a stable contract.
- describe providers that are already available in the current runtime; descriptor metadata does not own runtime installation state.

Do not add a second Datasource plugin contract in `yak-ops-spi`.

## Plugin API Version

Current Datasource Plugin API version: `3`.

V3 removes the legacy frontend form schema from the provider contract. `DataSourcePluginDescriptor` is now runtime metadata only:

```text
type
aliases
apiVersion
capabilities
secretFieldKeys
```

`ConnectionForm / FieldType / FormField / FormRule / FormSection / VisibilityCondition / JdbcUrlLinkage` are not Plugin API concepts. Frontend labels, placeholders, visibility rules and JDBC URL form linkage belong to the fixed Datasource UI, not to backend Provider metadata.

`connectionPropertyKeys()` is an additive V3 runtime discovery method. It only exposes recommended connection-property names for the advanced Key / Value editor and does not turn the Plugin API back into a dynamic frontend schema.

## Plugin Type Contract

Datasource providers are an open set. Plugin identity must not be modeled as a Common enum.

Must:
- each Provider owns one stable canonical string `type`, such as `MYSQL`.
- canonical type is normalized and persisted as a string.
- provider-specific compatibility names are declared through descriptor `aliases`.
- ServiceLoader discovery registers both canonical type and aliases to the same Provider.
- duplicate canonical types or aliases fail fast during plugin discovery.
- adding a new Provider must not require modifying Common, Service Layer code or a central database-type list.

V3 的 `secretFieldKeys` 只声明 Provider 运行时需要识别的敏感字段名；通用 password / token / secret / private-key 规则仍由 Datasource secret handling 统一兜底。

Must Not:
- recreate runtime plugin install flags such as `installRequired` / `installHint` without an implemented installation lifecycle.
- recreate `DataSourceDbType` or an equivalent central enum/list of supported databases.
- make Service Layer code parse vendor type aliases.
- require a core-module change only to register a new Provider.
- add frontend form metadata such as labels, placeholders, sections, validation rules, visibility conditions or JDBC URL templates back into the Plugin descriptor.

## Built-in Provider Baseline

The current Yak Ops product baseline only packages:
- `MYSQL`
- `ORACLE`
- `POSTGRE_SQL` with `POSTGRESQL` and `POSTGRES` aliases.

Other providers must not be packaged, registered, or pulled in through runtime dependencies unless the product baseline is intentionally expanded in a dedicated change.

## Connection Property Key Discovery

高级参数候选 Key 由 Provider 提供，前端不得复制 MySQL / Oracle / PostgreSQL JDBC 参数目录。

Runtime path:

```text
DataSourceController
→ DataSourceService
→ DataSourcePluginRegistry
→ DataSourcePlugin.connectionPropertyKeys()
→ AbstractJdbcDataSourcePlugin
→ JDBC Driver#getPropertyInfo(...)
```

Must:
- JDBC Provider 优先通过 JDBC Driver 的 `Driver#getPropertyInfo` 发现连接属性名，并合并 Provider 已知 canonical property keys。
- 返回值必须稳定排序、大小写不重复，并过滤已经由结构化连接字段拥有的 `host / port / database / username / password / jdbcUrl / driver` 等 Key。
- Driver 内部测试 / fault-injection 属性不得暴露给产品高级参数候选项。
- Driver 元数据读取失败时可以回退到 Provider 已知属性，不能为了打开高级参数下拉框建立真实数据库连接。
- Property Keys 只是推荐候选项，不是严格白名单；未知属性继续遵循 Provider 现有 pass-through / validation 规则。
- `POSTGRESQL` / `POSTGRES` 等 alias 必须由 Registry 路由到同一个 canonical Provider 后再查询候选项。

Must Not:
- 把 property value、默认凭证、连接实例或敏感信息返回给前端。
- 把候选 Key 放入 `DataSourcePluginDescriptor` 或重新引入动态 Form Schema。
- 在 Business、Boot 或 Frontend 维护第二份 Vendor JDBC property catalog。

## Provider Rules

Must:
- keep vendor URL/property/driver quirks inside the provider owner.
- shared JDBC base only parses the generic `properties` object and provides reusable validation helpers; provider-specific property canonical names, value normalization and validation stay in MySQL / Oracle / PostgreSQL Provider implementations.
- Provider may normalize and validate well-known properties while leaving unknown JDBC driver properties pass-through unless that Provider has a concrete reason to reject them.
- frontend must not maintain a second vendor-property catalog or duplicate Provider validation rules.
- normalize provider failures into stable plugin exceptions.
- JDBC Connection Test must establish a real JDBC connection and validate it with `Connection.isValid(timeoutSeconds)`; parsing parameters or obtaining a non-null Connection alone is not a successful connectivity result.
- declare capabilities explicitly.
- keep secrets out of error messages and logs.
- keep remaining descriptor metadata deterministic.
- reuse shared JDBC behavior before copying provider code.
- reuse Common `StringUtils` / `JSONUtils` / `SensitiveUtils` for blank handling, generic JSON read-write and credential masking; provider code must not create local string helpers or its own `ObjectMapper`.
- keep JDBC-specific finite modes such as SSH authentication as top-level enums under the JDBC owner package; do not hide them as nested contract types.

Must Not:
- embed product business rules.
- call Datasource Repository / DAO.
- special-case one provider inside Service Layer code when the behavior belongs in that provider.
- centralize MySQL / Oracle / PostgreSQL property names or allowed values in Business, Common, Boot or the frontend.
- add generic abstractions used by only one provider without a clear boundary.
- recreate deleted test modules or fixtures as a side effect.

## PostgreSQL Connection Contract

PostgreSQL Provider 的数据源连接目标是 Database，不把 Schema 提升为 Datasource 核心连接字段。

Must:
- canonical type 固定为 `POSTGRE_SQL`，兼容别名只保留 `POSTGRESQL` / `POSTGRES`。
- 默认端口固定为 `5432`，默认 Driver 为 `org.postgresql.Driver`。
- 结构化连接字段保持 `host / port / database / username / password / properties`。
- JDBC URL 使用 `jdbc:postgresql://host:port/database`，不得把 Schema 追加到 URL path。
- `schema` / `schemaName` 不属于 PostgreSQL Datasource 顶层连接字段；如需调整连接默认 search path，只能通过高级参数 `properties.currentSchema`。
- `currentSchema` 只影响 JDBC Session 的默认 Schema 搜索路径，不改变 Datasource 的 Database identity，也不替代 Catalog 中显式的 Schema 路径。
- Connection Test 必须真实打开 PostgreSQL JDBC Connection，并继续执行统一的 `Connection.isValid(timeoutSeconds)` 校验。
- PostgreSQL 的 Schema 发现与表定位继续属于 Catalog 能力，Datasource 创建 / 编辑不要求绑定 `public` 或其他 Schema。

Must Not:
- 为 PostgreSQL 在 Business / Frontend 新增第二套 Schema 连接字段。
- 把 `public` 当成 Yak Ops 固定默认值写入持久化连接参数。
- 因为指定 `currentSchema` 就把 Catalog 可见范围永久收窄到单个 Schema。

## Logging Boundary

Plugin / JDBC 底层默认不记录连接参数流水日志。

Must:
- Provider 注册成功、type / aliases / apiVersion / capabilities 等生命周期信息由上层 `DataSourcePluginRegistry` 统一记录。
- 需要记录异常时只记录已经脱敏的稳定错误信息；敏感文本必须先通过 Common `SensitiveUtils` 处理。
- 日志只记录定位运行时问题真正需要的状态，例如 Provider identity、capability、失败阶段，不记录完整请求或连接对象。

Must Not:
- 记录 connection JSON、normalized JSON、JDBC URL、username、password、token、private key、passphrase、known_hosts 内容。
- 在 MySQL / Oracle / PostgreSQL Provider 中分别重复打印连接开始、连接成功等流水日志。
- 使用 `System.out`、`printStackTrace` 或绕过统一日志边界。
- 为正常 Catalog 遍历、字段读取或 JDBC method 调用增加 debug 噪声日志。

底层 Plugin 没有日志本身不是缺陷；当上层 Registry 已经拥有生命周期日志时，优先保持 Provider 静默。

## Aggregation

`yak-ops-plugin-datasource-all` only assembles the current built-in provider baseline.

It must not own business behavior or duplicate provider logic.
