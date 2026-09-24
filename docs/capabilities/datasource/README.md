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
- `yak-ops-ui/src/services/data-source`

Data:
- `yak-ops-business/yak-ops-business-datasource/src/main/resources/db/migration/yak-datasource`
- `yak-ops-business/yak-ops-business-datasource/src/main/resources/mapper`

Tests:
- Datasource module `src/test/java`
- Datasource plugin submodule tests
- UI Datasource tests

## Current Capability Map

Current code shows these product capability areas:

```text
Datasource Management
Connection Test / Connection Normalization
Plugin Configuration
Catalog Browse
SQL Execution
SQL Execution Audit / Observability
```

These are a routing map, not yet individual accepted Capability Contracts.

Do not treat this list as a guarantee that every behavior is final.

## Shared Rules

Until individual Capability Contracts are written, all Datasource work follows:

- root `ARCHITECTURE.md`
- root `JAVA_RULES.md`
- root `BACKEND_TEST_RULES.md`
- `DATASOURCE_RULES.md`
- `PLUGIN_RULES.md` when plugin behavior changes
- frontend rules when UI changes

## Development Order

For the next Datasource change:

```text
choose one capability
→ inspect current code + tests
→ write that capability contract
→ review contract
→ implement the gap
→ test
→ verify
→ mark Done
```

Do not write every Datasource Capability up front from assumptions.

## Boundary

Datasource is currently the only active Yak Ops product domain.

Removed domains are not dependencies, reference architectures or future requirements unless they are explicitly reintroduced by a new product decision.
