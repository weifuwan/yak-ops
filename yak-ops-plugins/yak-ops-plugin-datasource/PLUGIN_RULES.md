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

Current Datasource Plugin API version: `2`.

V2 removes future-facing runtime installation metadata (`installRequired / installHint`) and the unreachable `DRIVER` form field type. Providers describe capabilities already available in the running application; deployment-time JDBC jars may still be supplied through the runtime classpath.

## Plugin Type Contract

Datasource providers are an open set. Plugin identity must not be modeled as a Common enum.

Must:
- each Provider owns one stable canonical string `type`, such as `MYSQL`.
- canonical type is normalized and persisted as a string.
- provider-specific compatibility names are declared through descriptor `aliases`.
- ServiceLoader discovery registers both canonical type and aliases to the same Provider.
- duplicate canonical types or aliases fail fast during plugin discovery.
- adding a new Provider must not require modifying Common, Service Layer code or a central database-type list.

V2 仍保留 ConnectionForm / FieldType 等 descriptor 元数据用于现有敏感字段识别和兼容；它们不再通过 HTTP 驱动前端表单，也不得继续扩展新的 UI schema 能力。该遗留 contract 在独立 Plugin Descriptor V3 变更中收口。

Must Not:
- recreate runtime plugin install flags such as `installRequired` / `installHint` without an implemented installation lifecycle.
- recreate `DataSourceDbType` or an equivalent central enum/list of supported databases.
- make Service Layer code parse vendor type aliases.
- require a core-module change only to register a new Provider.

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
