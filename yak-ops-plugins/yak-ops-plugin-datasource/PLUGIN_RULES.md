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
→ shared JDBC implementation + JDBC providers

yak-ops-plugin-datasource-doris
→ Doris-specific behavior

yak-ops-plugin-datasource-elasticsearch
→ Elasticsearch-specific behavior

yak-ops-plugin-datasource-mongodb
→ MongoDB-specific behavior

yak-ops-plugin-datasource-all
→ runtime aggregation only
```

## API Rules

`yak-ops-plugin-datasource-api` must:
- stay provider-neutral.
- expose stable capability and connection contracts.
- avoid Datasource Business implementation types.
- avoid concrete driver classes.
- keep default methods only when they preserve a stable contract.

Do not add a second Datasource plugin contract in `yak-ops-spi`.

## Plugin Type Contract

Datasource providers are an open set. Plugin identity must not be modeled as a Common enum.

Must:
- each Provider owns one stable canonical string `type`, such as `MYSQL` or `ELASTICSEARCH8`.
- canonical type is normalized and persisted as a string.
- provider-specific compatibility names are declared through descriptor `aliases`.
- ServiceLoader discovery registers both canonical type and aliases to the same Provider.
- duplicate canonical types or aliases fail fast during plugin discovery.
- adding a new Provider must not require modifying Common, Business or a central database-type list.

Closed protocol vocabularies such as `DataSourceCapability`, form `FieldType` and `VisibilityOperator` may remain enums because their value set belongs to the SPI contract itself.

Must Not:
- recreate `DataSourceDbType` or an equivalent central enum/list of supported databases.
- make Business parse vendor type aliases.
- require a core-module change only to register a new Provider.

## Provider Rules

Must:
- keep vendor URL/property/driver quirks inside the provider owner.
- normalize provider failures into stable plugin exceptions.
- declare capabilities explicitly.
- keep secrets out of error messages and logs.
- keep descriptor/config metadata deterministic.
- reuse shared JDBC behavior before copying provider code.

Must Not:
- embed product business rules.
- call Datasource Repository / DAO.
- special-case one provider inside Business when the behavior belongs in that provider.
- add generic abstractions used by only one provider without a clear boundary.
- recreate deleted test modules or fixtures as a side effect.

## Aggregation

`yak-ops-plugin-datasource-all` only assembles built-in providers.

It must not own business behavior or duplicate provider logic.
