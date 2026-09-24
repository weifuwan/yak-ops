# Frontend Service Rules

Scope:
- `yak-ops-ui/src/services/**`
- Datasource page-local service code when it owns an endpoint contract

Depends On:
- `./ARCHITECTURE.md`
- `./FRONTEND_RULES.md`

Owns:
- backend endpoint paths
- request / response types
- HTTP contract adaptation
- stable Datasource API calls

## Must

- reusable Datasource API calls live under `src/services/data-source`.
- preserve backend request/response semantics instead of inventing a second frontend contract.
- reuse existing request infrastructure and common error handling.
- keep endpoint path, method and parameter mapping in the Service boundary.
- expose business-meaningful functions to pages/components.
- consolidate duplicate Datasource calls instead of creating parallel clients.

## Must Not

- put visual state in Service.
- import page components into Service.
- silently swallow backend errors and return fake success data.
- duplicate the same endpoint without an ownership reason.
- create interface/impl layers for simple TypeScript functions.
- call removed-domain endpoints from new Datasource code.
- recreate deleted service tests or mocks as a side effect.
