# Datasource Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/datasource/**`
- `yak-ops-ui/apps/web/service/datasource/**`

Owns:

- Datasource CRUD UI
- Datasource filters / table / pagination
- Datasource create / edit / connection-test form
- Datasource backend Contract adaptation

## Principle

Datasource V1 is a simple database connection management page, not a frontend plugin platform.

Current product flow:

```text
Filter
→ Table
→ Create / Edit / Delete / Test Connection
```

不要为了未来扩展提前引入动态表单、Editor Runtime、Domain Hook、Summary Layer 或 Provider UI abstraction。

## Structure

```text
app/datasource/
├── index.tsx
├── table.tsx
├── form.tsx
├── constants.ts
├── types.ts
├── icons/
└── i18n/

service/datasource/
├── index.ts
└── types.ts
```

## Supported Type Baseline

当前只展示：

- `MYSQL`
- `ORACLE`
- `POSTGRE_SQL`（UI 展示 PostgreSQL）

新增类型必须先扩展后端 Provider，再独立更新前端产品入口。

## Page Ownership

`index.tsx` owns:

- keyword / dbType filters
- paging state
- list loading
- create/edit drawer visibility
- delete confirmation
- list refresh

不要为这些页面局部状态再创建 `hooks/use-datasources.ts`。

## Form Ownership

`form.tsx` owns:

- create / edit state
- field validation
- connection test
- save

固定字段：

```text
name
dbType
jdbcUrl
username
password
remark
```

Create 默认使用 `DEVELOP` environment；Edit 沿用后端详情中的 environment。Environment 不作为当前 UI 产品字段。

后端 JDBC Plugin 负责 JDBC URL 校验、driver、Provider 差异、Normalize 和真正的 Connection Test。

## Must

- 列表只使用 `@yak-ops/yak-ui` 的 `Table`；Datasource 业务层禁止手写 `<table> / <thead> / <tbody>`。
- Datasource Table 使用 Yak UI 的中等密度、无圆角卡片覆盖；分页占用列表剩余高度的底部位置，并通过 `pageSizeLabel` 显示“每页显示：”。
- Datasource 页面保持 `#F6F6F6` 页面底色，筛选、Table 和 Pagination 必须放在同一个白色内容面板中；内容面板不加阴影和额外圆角。
- 筛选只保留 dbType 和 keyword；工具栏顺序固定为“新增数据源 → 数据源类型 → 数据源名称”，新增入口不放在 PageHeader extra。
- CRUD 和 Connection Test 统一走 `service/datasource`。
- HTTP transport only through `service/http`。
- Common primitives from `@yak-ops/yak-ui`。
- Backend Contract owner stays in `service/datasource/types.ts`。

## Must Not

- Recreate `editor/` or `hooks/` under Datasource。
- Recreate Summary cards。
- Recreate dynamic form schema / renderer / form runtime。
- Recreate Host/Port/Database ↔ JDBC URL linkage UI。
- Recreate SSH Tunnel UI。
- Recreate Driver Manager / Driver Class configuration UI。
- Recreate custom JDBC properties editor。
- Recreate `management / model / plugin / connection` directories。
- Recreate `packages/datasource`。
- Call `fetch` directly from Datasource UI。
- Import `service/http` directly from App。
- Reintroduce Ant Design or a second UI framework。
