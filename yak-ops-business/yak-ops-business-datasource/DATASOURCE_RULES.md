# Datasource Rules

Scope:
- `yak-ops-business/yak-ops-business-datasource/**`

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- HTTP contract 变化加载 `/CONTROLLER_RULES.md`
- Plugin 变化加载 `/yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN_RULES.md`

Owns:
- Datasource product behavior
- Datasource persistence
- Connection / Catalog / SQL Execution behavior
- Datasource-to-plugin business boundary

## Package Ownership

```text
management   mutation / lifecycle commands
query        read behavior
domain       business facts
connection   connection parsing / normalization / test orchestration
catalog      catalog query and read policy
execution    SQL execution runtime and observability
gateway      ports toward plugin capability
repository   persistence-facing business contract
dao          MyBatis persistence implementation
config       module infrastructure
security     Datasource security
exception    Datasource business errors
```

## HTTP Boundary

Datasource HTTP Controller、ControllerAdvice 和 Controller-only converter 统一由 `yak-ops-boot` 持有。

本模块只提供 Datasource capability，不创建 `controller` package，也不依赖 Boot。

## Business Rules

Must:
- mutation behavior has one clear owner.
- read behavior stays separate only when it has a real read contract.
- validation close to business meaning stays in Datasource, not Controller.
- plugin differences enter through stable plugin/gateway contracts.
- secret handling must never leak raw credentials into logs, errors or response objects.
- SQL Execution must preserve explicit lifecycle/status/transaction semantics.

Must Not:
- add Manager / Reader / Adapter just because neighboring code has one.
- split a cohesive owner only because the file is long.
- push Datasource business policy into Plugin API.
- access concrete plugin implementations from business code.
- bypass Repository with ad hoc Mapper access from business behavior.
- recreate deleted tests as architecture placeholders.

## Persistence

```text
business behavior
→ Repository
→ DAO / Mapper
→ MyBatis / SQL
```

- Repository owns persistence-facing business semantics.
- DAO / Mapper own storage implementation.
- simple queries prefer MyBatis-Plus capabilities.
- complex SQL may use Mapper XML.
- do not add forwarding methods that only rename existing CRUD.
- Flyway owns schema evolution under `src/main/resources/db/migration/yak-datasource`.

## Execution

SQL Execution code physically lives in this Maven module while some Java packages still use `io.yak.ops.core.execution.*`.

Treat it as Datasource-owned current code until a dedicated package migration is reviewed.
