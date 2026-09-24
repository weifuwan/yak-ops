# Datasource Frontend Rules

Scope:
- `yak-ops-ui/apps/web/app/datasource/**`
- `yak-ops-ui/apps/web/service/datasource/**`

Owns:
- Datasource product UI and interaction
- Datasource presentation metadata
- Datasource editor / connection state
- Datasource backend contract and endpoint adaptation

## Principle

Prefer local cohesion over architectural layering.

不要因为一个概念叫 Management / Model / Plugin，就为它创建一层目录。

只有满足下面至少一条时才继续抽目录：

- 形成独立行为或生命周期。
- 被多个区域复用。
- 代码复杂度已经明显影响当前文件可读性。

一个页面相关的组件、Hook、类型和 helper 应优先放在页面附近。

## Structure

```text
app/datasource/
├── index.tsx
├── card.tsx
├── toolbar.tsx
├── summary.tsx
├── empty-state.tsx
├── constants.tsx
├── types.ts
├── utils.ts
├── hooks/
│   ├── use-datasources.ts
│   └── use-plugin-form-config.ts
├── editor/
├── connection/
├── icons/
└── i18n/

service/datasource/
├── api.ts
├── catalog.ts
├── driver.ts
├── types.ts
└── index.ts
```

`editor/` 和 `connection/` 仍是当前真实复杂能力，PR1 不为了扁平而强行合并它们。

## Page Ownership

`index.tsx`
- Datasource 页面入口。
- 页面组合。
- Create / Edit / Delete / Test 等页面级交互。
- 页面 Header 等仅服务当前页面的小组件。

`card.tsx`
- Datasource Card。
- Card 私有的 Connection Status 展示。

`hooks/use-datasources.ts`
- 列表加载。
- 分页 / 筛选刷新。
- Edit detail / Delete / Connection Test 等异步生命周期。

`hooks/use-plugin-form-config.ts`
- Plugin form config 的复杂加载 / 安装生命周期。
- 该 Hook 私有 reducer 与状态机留在同一文件，不再单独建立 `plugin/` 层。

## Types

Backend Contract 的真实 owner：

```text
service/datasource/types.ts
```

App 页面类型统一从：

```text
app/datasource/types.ts
```

使用。

`types.ts` 可以 re-export Service Contract，并拥有 Datasource UI-only 类型；不要再创建 `model/types.ts`。

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

- 页面专属代码优先保持局部内聚。
- Datasource endpoint / backend Contract stays under `service/datasource`。
- HTTP transport goes through `service/http`。
- Common UI primitives come from `@yak-ops/yak-ui`。
- Dynamic form state stays in Datasource, not Yak UI。
- 一个文件只被单一父组件使用且逻辑简单时，优先内联或保持同层，而不是继续建目录。

## Must Not

- Recreate `management/`、`model/`、`plugin/`。
- Recreate `packages/datasource`。
- Recreate `@yak-ops/datasource` alias or package dependency。
- Let `service/datasource` import `app/datasource`。
- Let Datasource UI import `service/http` directly。
- Put Datasource-specific helpers into root `utils/hooks/types/constants`。
- Create a directory solely to represent a concept。
- Call `fetch` directly from Datasource UI。
- Recreate generic UI primitives。
