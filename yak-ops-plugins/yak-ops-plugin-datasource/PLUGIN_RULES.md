# Datasource Plugin Rules

Scope:
- `yak-ops-plugins/yak-ops-plugin-datasource/**`

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- `/BACKEND_TEST_RULES.md`

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
- keep default methods only when they preserve a stable backward-compatible contract.

Do not add a second Datasource plugin contract in `yak-ops-spi`.

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

## Aggregation

`yak-ops-plugin-datasource-all` only assembles built-in providers.

It must not own business behavior or duplicate provider logic.

## Tests

Prefer deterministic tests for:
- descriptor / capability declarations
- connection parsing
- JDBC URL/property behavior
- catalog mapping
- execution adapter behavior
- provider error mapping

Do not require real external vendor services in normal unit tests.
