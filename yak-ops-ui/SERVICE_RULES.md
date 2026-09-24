# Frontend Service Rules

Scope:
- `yak-ops-ui/src/services/**`
- Datasource page-local service code when it owns an endpoint contract

Depends On:
- `./ARCHITECTURE.md`
- `./FRONTEND_RULES.md`
- `./TEST_RULES.md` when behavior changes

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
- duplicate the same endpoint in multiple files without an ownership reason.
- create interface/impl layers for simple TypeScript functions.
- call removed-domain endpoints from new Datasource code.

## Tests

Service tests are valuable when mapping is non-trivial:
- URL / method / parameter mapping
- response normalization
- error handling

Do not test the HTTP library itself.
