# Datasource UI Rules

Scope:
- `yak-ops-ui/packages/datasource/**`

Owns:
- Datasource frontend product capability
- Datasource API contract/adaptation
- Datasource management / editor / connection / plugin behavior

## Structure

```text
src/
├── api/
├── connection/
├── editor/
├── i18n/
├── management/
├── model/
├── plugin/
└── index.tsx
```

Capability owns its component, hook, type and helper. Do not recreate top-level `components / hooks / utils / types` buckets.

## Ownership

`management`
- list / summary / filter / pagination / card actions
- page-level loading and refresh lifecycle

`editor`
- create / edit flow
- datasource type selection
- dynamic connection form
- payload normalization

`connection`
- JDBC URL linkage
- SSH tunnel configuration
- driver selection / upload UI

`plugin`
- plugin config load/install lifecycle
- plugin config state

`api`
- backend Datasource API calls
- catalog / driver endpoints

`model`
- Datasource contracts shared by multiple Datasource capabilities
- datasource type options and shared presentation metadata

## Dependency Direction

```text
apps/web
   ↓
@yak-ops/datasource
   ↓
management / editor / connection / plugin
   ↓
api + model
   ↓
@yak-ops/yak-ui
```

`api` may temporarily use the shared HTTP transport under `src/service/http` until a real cross-package HTTP owner is extracted.

## Must

- App only enters Datasource through the package public entry.
- Common UI primitives come from `@yak-ops/yak-ui`.
- Internal directories follow capability ownership.
- Product state stays with the capability that owns the behavior.
- Backend request/response adaptation stays under `api`.
- Cross-capability stable contracts stay under `model`.

## Must Not

- Depend on `apps/web`, Router, AppLayout or AuthProvider.
- Import `src/pages/data-source` or `src/service/datasource`.
- Recreate generic Button / Input / Select / Dialog primitives.
- Add top-level generic `components / hooks / utils / types` buckets.
- Move one-off capability helpers into `model` just to shorten relative imports.
- Change backend or product behavior as part of a directory-only refactor.

## AntD Migration

PR3 changes ownership, not UI behavior.

Existing AntD usage moved with the owning Datasource capability. New AntD usage remains forbidden. The following cleanup PR replaces these usages with Yak UI and removes the dependencies.
