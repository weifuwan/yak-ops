# Datasource Frontend Rules

Scope:
- `yak-ops-ui/apps/web/app/datasource/**`
- `yak-ops-ui/apps/web/service/datasource/**`

Owns:
- Datasource product UI and interaction
- Datasource editor and dynamic form runtime
- Datasource backend contract and endpoint adaptation

## Principle

Prefer local cohesion over architectural layering.

不要因为一个概念叫 Management / Model / Plugin / Connection，就为它创建一层目录。

只有满足下面至少一条时才继续抽目录：

- 形成独立行为或生命周期。
- 被多个区域复用。
- 代码复杂度已经明显影响当前文件可读性。

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
│   ├── index.tsx
│   ├── type-selector.tsx
│   ├── dynamic-form.tsx
│   ├── custom-kv-list.tsx
│   ├── form-runtime.tsx
│   ├── form-model.ts
│   ├── form-utils.ts
│   ├── driver-manager.tsx
│   ├── jdbc-url-field.tsx
│   ├── jdbc-url-utils.ts
│   ├── ssh-tunnel-manager.tsx
│   └── types.ts
├── icons/
└── i18n/

service/datasource/
├── api.ts
├── catalog.ts
├── driver.ts
├── types.ts
└── index.ts
```

## Editor Ownership

`editor/` 是“编辑一个数据源”的局部工作区。

它允许多个文件，因为动态表单本身足够复杂；但内部保持扁平，不再继续拆：

```text
editor/DynamicDataSourceForm/components
editor/DynamicDataSourceForm/utils
connection/DriverManager
connection/JdbcUrlField
connection/SshTunnelManager
```

Driver / JDBC URL / SSH 不是独立 Domain，它们只是 Datasource Editor 的特殊字段能力。

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

Editor 私有类型只放：

```text
app/datasource/editor/types.ts
```

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
- Editor 相关组件和 helper 优先留在 `editor/` 同一层。
- Datasource endpoint / backend Contract stays under `service/datasource`。
- HTTP transport goes through `service/http`。
- Common UI primitives come from `@yak-ops/yak-ui`。
- Dynamic form state stays in Datasource, not Yak UI。

## Must Not

- Recreate `management/`、`model/`、`plugin/`、`connection/`。
- Recreate `editor/DynamicDataSourceForm/`。
- Recreate one-file directories such as `DriverManager/` or `SshTunnelManager/`。
- Recreate `packages/datasource`。
- Recreate `@yak-ops/datasource` alias or package dependency。
- Let `service/datasource` import `app/datasource`。
- Let Datasource UI import `service/http` directly。
- Create a directory solely to represent a concept。
- Call `fetch` directly from Datasource UI。
