# Datasource Frontend Rules

Scope:
- `yak-ops-ui/apps/web/app/datasource/**`
- `yak-ops-ui/apps/web/service/datasource/**`

Owns:
- Datasource product UI and interaction
- Datasource editor and fixed connection form
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
├── table.tsx
├── toolbar.tsx
├── summary.tsx
├── empty-state.tsx
├── constants.tsx
├── types.ts
├── utils.ts
├── hooks/
│   └── use-datasources.ts
├── editor/
│   ├── index.tsx
│   ├── type-selector.tsx
│   ├── connection-form.tsx
│   ├── custom-kv-list.tsx
│   ├── form-runtime.tsx
│   ├── form-model.ts
│   ├── jdbc-url-field.tsx
│   ├── jdbc-url-utils.ts
│   ├── ssh-tunnel-manager.tsx
│   └── types.ts
├── icons/
└── i18n/

service/datasource/
├── index.ts
└── types.ts
```

## Supported Type Baseline

当前产品内置数据源只展示：

- `MYSQL`
- `ORACLE`
- `POSTGRE_SQL`（UI 展示为 PostgreSQL）

Datasource Selector、Toolbar Filter 和本地数据库图标必须保持与后端 built-in provider baseline 一致。

新增数据库类型时，必须先扩展后端 Provider，再单独更新前端产品入口；禁止只在前端增加不可用类型。

## Editor Ownership

`editor/` 是“编辑一个数据源”的局部工作区。

连接字段固定服务于当前内置 MySQL / Oracle / PostgreSQL，不维护通用动态表单 schema。Editor 内部保持扁平，不再继续拆：

```text
connection/JdbcUrlField
connection/SshTunnelManager
```

JDBC URL / SSH 不是独立 Domain，它们只是 Datasource Editor 的特殊字段能力。

## Service Ownership

`service/datasource/index.ts` 统一拥有：

- Datasource CRUD。
- Connection Test。

这些 endpoint 共享同一个 Domain、同一个 HTTP transport 和同一套 Contract，没有独立生命周期，因此不再拆成 `api.ts / catalog.ts / driver.ts`。

`service/datasource/types.ts` 单独保留，因为它是当前前端真实消费的稳定 backend Contract owner。

当前产品不提供运行时插件安装、驱动上传或 Plugin Config schema API：Provider 必须在应用启动前可用；前端使用固定连接表单，后端 Provider 负责解析、默认值、校验和 Normalize。

后端 Catalog metadata capability 可以独立存在；当前 Datasource UI 没有 Catalog 浏览入口时，不在 frontend service 中提前镜像 databases / schemas / tables / columns API。

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
- Datasource 列表使用业务级原生 Table，不维护 Card/Grid/List 多套展示模式。
- Editor 相关组件和 helper 优先留在 `editor/` 同一层。
- Datasource endpoint / backend Contract stays under `service/datasource`。
- Datasource endpoint 默认集中在 `service/datasource/index.ts`。
- HTTP transport goes through `service/http`。
- Common UI primitives come from `@yak-ops/yak-ui`。
- Connection form state stays in Datasource, not Yak UI。
- Datasource 不依赖 `framer-motion`，页面动效优先使用 CSS transition。

## Must Not

- Recreate `management/`、`model/`、`plugin/`、`connection/`。
- Recreate `service/datasource/api.ts`、`catalog.ts`、`driver.ts` 这类概念拆分文件。
- Recreate dynamic datasource form schema / renderer runtime。
- Recreate one-file directories such as `DriverManager/` or `SshTunnelManager/`。
- Recreate `packages/datasource`。
- Recreate `@yak-ops/datasource` alias or package dependency。
- Let `service/datasource` import `app/datasource`。
- Let Datasource UI import `service/http` directly。
- Create a directory solely to represent a concept。
- Call `fetch` directly from Datasource UI。
- Import `framer-motion` from Datasource。
- Recreate Datasource `card.tsx` or Grid/List view switching。
