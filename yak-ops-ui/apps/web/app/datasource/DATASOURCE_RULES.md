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
→ Create Wizard / Edit / Delete / Batch Delete / Batch Test Connection
```

Create Wizard:

```text
选择数据源类型
→ 配置连接信息
→ 测试连接 / 完成
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
- create wizard / edit drawer visibility
- delete confirmation
- multi-selection / batch operation state
- list refresh

不要为这些页面局部状态再创建 `hooks/use-datasources.ts`。

## Form Ownership

`form.tsx` owns:

- create wizard state
- edit form state
- field validation
- connection test
- save

Create 使用 Yak UI `Modal`，第一步只选择当前支持的数据源类型，点击卡片直接进入配置步骤；第二步固定展示基础信息与连接配置。Edit 继续使用 Drawer，不经过类型选择步骤。

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
- 筛选只保留 dbType 和 keyword；工具栏顺序固定为“新增数据源 → 数据源类型 → 数据源名称”，新增入口不放在 PageHeader extra。筛选 Input / SelectTrigger 统一使用 Yak UI `outlined` variant，不在页面里覆盖基础 border/background。
- 列表行操作只保留“编辑｜删除”文字操作，中间使用轻量 Divider；操作组在操作列内居中对齐；列表不提供单行 Connection Test，连接测试保留在新增 / 编辑表单内。
- Table 启用受控 `rowSelection`；表头和底部 Checkbox 都只全选当前页，跨页已选 ID 保留；筛选条件变化清空选择，单次最多选择 100 条。
- Table `footer` 左侧承载“批量删除 / 批量测试连通性”，右侧继续使用 Yak UI Pagination；批量删除必须二次确认，批量连接测试直接执行并反馈成功 / 失败数量。
- 新增使用 Yak UI `Modal` 两步 Wizard；Modal Header / Footer 固定，只允许 Body 滚动。第一步只展示当前支持的 `MYSQL / ORACLE / POSTGRE_SQL`，不引入动态 Provider UI。
- Edit 继续使用 Drawer，并直接打开和关闭，不使用滑入或淡入淡出过渡动画。
- CRUD、Batch Operations 和 Connection Test 统一走 `service/datasource`。
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
