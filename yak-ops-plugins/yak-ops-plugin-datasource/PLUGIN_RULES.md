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

## Provider Rules

Must:
- keep vendor URL/property/driver quirks inside the provider owner.
- normalize provider failures into stable plugin exceptions.
- declare capabilities explicitly.
- keep secrets out of error messages and logs.
- keep remaining descriptor metadata deterministic.
- reuse shared JDBC behavior before copying provider code.
- reuse Common `JsonUtils` / `SensitiveUtils` for generic JSON read-write and credential masking; provider code must not create its own `ObjectMapper`.

Must Not:
- embed product business rules.
- call Datasource Repository / DAO.
- special-case one provider inside Service Layer code when the behavior belongs in that provider.
- add generic abstractions used by only one provider without a clear boundary.
- recreate deleted test modules or fixtures as a side effect.

## Aggregation

`yak-ops-plugin-datasource-all` only assembles the current built-in provider baseline.

It must not own business behavior or duplicate provider logic.
