# Datasource Frontend Rules

Scope:
- `yak-ops-ui/apps/web/app/datasource/**`
- `yak-ops-ui/apps/web/service/datasource/**`

Owns:
- Datasource product UI and interaction
- Datasource presentation metadata
- Datasource editor / connection / plugin state
- Datasource backend contract and endpoint adaptation

## Structure

```text
app/datasource/
├── connection/
├── editor/
├── i18n/
├── management/
├── model/
├── plugin/
└── index.tsx

service/datasource/
├── api.ts
├── catalog.ts
├── driver.ts
├── types.ts
└── index.ts
```

## Ownership

`app/datasource`
- Datasource UI
- product state
- dynamic form runtime
- presentation metadata
- capability-local helpers

`service/datasource`
- backend Contract
- CRUD endpoints
- connection test
- plugin config endpoints
- catalog endpoints
- driver upload

`app/datasource/model/types.ts` 只是 Service Contract 的 App-side type facade，不是第二份类型事实来源。

## Dependency Direction

```text
app/router
   ↓
app/datasource
   ↓
service/datasource
   ↓
service/http

app/datasource
   ↓
@yak-ops/yak-ui
```

禁止 `service/datasource → app/datasource`。

## Must

- Datasource UI code stays under `app/datasource`。
- Datasource endpoint / backend Contract stays under `service/datasource`。
- HTTP transport goes through `service/http`。
- Common UI primitives come from `@yak-ops/yak-ui`。
- Capability-local component / hook / helper stays near its owner。
- Dynamic form state stays in Datasource, not Yak UI。

## Must Not

- Recreate `packages/datasource`。
- Recreate `@yak-ops/datasource` alias or package dependency。
- Let `service/datasource` import `app/datasource`。
- Let Datasource UI import `service/http` directly。
- Put Datasource-specific helpers into root `utils/hooks/types/constants`。
- Call `fetch` directly from Datasource UI。
- Recreate generic UI primitives。
