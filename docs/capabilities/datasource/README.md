# Datasource Domain

Status: Review

Scope:
- Datasource registration and lifecycle
- Connection handling
- Catalog browsing
- Datasource plugin metadata
- SQL execution and execution audit
- Datasource frontend

## Current Owners

Backend:
- `yak-ops-business/yak-ops-business-datasource`

Plugin:
- `yak-ops-plugins/yak-ops-plugin-datasource`

Frontend:
- `yak-ops-ui/src/pages/data-source`
- `yak-ops-ui/src/service/datasource`

Data:
- `yak-ops-dao/src/main/resources/db/migration/yak-ops`
- `yak-ops-business/yak-ops-business-datasource/src/main/resources/mapper`

## Current Capability Map

```text
Datasource Management
Connection Test / Connection Normalization
Plugin Configuration
Catalog Browse
SQL Execution
SQL Execution Audit / Observability
```

这只是当前代码能力地图，不代表每个 Contract 已经确认。

## Shared Rules

- root `ARCHITECTURE.md`
- root `JAVA_RULES.md`
- `DATASOURCE_RULES.md`
- `FLYWAY_RULES.md` when schema changes
- `PLUGIN_RULES.md` when plugin behavior changes
- frontend rules when UI changes

## Development Order

```text
choose one capability
→ inspect current code
→ write the capability contract
→ review contract
→ implement the gap
→ review
→ explicit verification
→ mark Done
```

## Boundary

Datasource is currently the only active Yak Ops product domain.

Removed domains are not dependencies, reference architectures or future requirements unless explicitly reintroduced.
