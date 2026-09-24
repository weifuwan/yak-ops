# Datasource UI Rules

Scope:
- `yak-ops-ui/packages/datasource/**`

Owns:
- Datasource frontend product capability
- Datasource API contract/adaptation
- Datasource management / editor / connection / plugin behavior
- Datasource dynamic form runtime

## Structure

```text
src/
├── api/
├── connection/
├── editor/
│   ├── DynamicDataSourceForm/
│   ├── DataSourceEditor.tsx
│   ├── formModel.ts
│   ├── formRuntime.tsx
│   └── types.ts
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
- product form state and validation runtime

`connection`
- JDBC URL linkage
- SSH tunnel configuration
- driver selection / native file upload UI

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

## Form Runtime

`editor/formRuntime.tsx` exists because dynamic Datasource form behavior is a product concern, not a generic UI concern.

It owns only the current required behavior:

- values
- field registration
- validation
- field subscriptions
- reset / patch
- dynamic visibility support

Do not turn it into an AntD Form compatibility layer.

If a new form behavior is required, add it only when Datasource has a concrete use case.

## Must

- App only enters Datasource through the package public entry.
- Common UI primitives come from `@yak-ops/yak-ui`.
- Internal directories follow capability ownership.
- Product state stays with the capability that owns the behavior.
- Backend request/response adaptation stays under `api`.
- Cross-capability stable contracts stay under `model`.
- Driver file selection uses native browser file input; upload protocol remains under Datasource.

## Must Not

- Depend on `apps/web`, Router, AppLayout or AuthProvider.
- Import `src/pages/data-source` or `src/service/datasource`.
- Import `antd` or `@ant-design/icons`.
- Recreate Ant Design APIs or compatibility wrappers.
- Recreate generic Button / Input / Select / Dialog primitives.
- Add top-level generic `components / hooks / utils / types` buckets.
- Move one-off capability helpers into `model` just to shorten relative imports.

## UI Boundary

Ant Design removal is complete.

```text
Datasource business UI
        ↓
@yak-ops/yak-ui
        ↓
@base-ui/react
```

Product-only interaction and form state remain inside Datasource.
