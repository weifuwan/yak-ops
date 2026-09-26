# Datasource Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/datasource/**`
- `yak-ops-ui/apps/web/service/datasource/**`

Depends On:

- `/yak-ops-ui/apps/web/FORM_RULES.md`

Owns:

- Workspace-scoped Datasource CRUD UI
- Datasource filters / table / pagination
- Datasource create / edit / connection-test form
- Datasource backend Contract adaptation

## Principle

Datasource V1 is a Workspace-scoped database connection management page, not a frontend plugin platform.

The active Workspace is application context, not Datasource form state. Datasource DTOs never carry `workspaceId`; `service/http` injects the validated current Workspace header automatically. Switching Workspace remounts the workspace-scoped product outlet so list/filter/selection/form state cannot leak across Workspaces.

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

PostgreSQL 的 `POSTGRESQL` / `POSTGRES` 只作为兼容输入别名；进入前端表单状态后必须统一规范为 `POSTGRE_SQL`，列表始终展示产品名 `PostgreSQL`，不得把 canonical type 文本直接暴露给用户。

新增类型必须先扩展后端 Provider，再独立更新前端产品入口。

## Page Ownership

`index.tsx` owns:

- keyword / dbType filters
- paging state
- list loading
- create / edit modal visibility
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

Create / Edit 统一使用 Yak UI `Modal`。Create 第一步只选择当前支持的数据源类型，提供轻量分类与搜索，点击数据源 Item 直接进入配置步骤；Edit 跳过类型选择，直接进入同一份配置表单。Create / Edit 共用字段渲染、校验、连接测试和保存逻辑。

结构化连接字段：

```text
name
dbType
host
port
database
username
password
properties
remark
```

UI 中的访问身份当前固定为“用户名和密码”，认证选项当前固定为“无认证”，版本当前固定为“自动选择”；这三个选择只表达当前产品能力边界，不进入后端连接 Contract。

Create 默认使用 `DEVELOP` environment；Edit 沿用后端详情中的 environment。Environment 不作为当前 UI 产品字段。

前端只维护当前三种 JDBC Provider 的 Host / Port / Database 输入、JDBC Preview 和轻量 Key/Value 高级参数；高级参数编辑器保持 Provider-neutral，不维护 MySQL / Oracle / PostgreSQL 参数提示清单、枚举值或校验规则。HTTP 层直接提交结构化 `connectionParams` 对象，不允许在 App / Service 层手动 `JSON.stringify`。真正的 JDBC URL 生成、属性 Normalize / Validate、driver、Provider 差异和 Connection Test 仍由后端 JDBC Plugin 负责。

PostgreSQL 表单遵循 Database connection target：不新增 Schema 字段，不默认写入 `public`，JDBC Preview 只展示 `jdbc:postgresql://host:port/database`。如用户确实需要默认 search path，只通过通用高级参数 Key/Value 传递 `currentSchema`，前端不对其值做 PostgreSQL-specific 校验。

## Must

- 列表只使用 `@yak-ops/yak-ui` 的 `Table`；Datasource 业务层禁止手写 `<table> / <thead> / <tbody>`。
- Datasource Table 使用 Yak UI 的中等密度、无圆角卡片覆盖；分页占用列表剩余高度的底部位置，并通过 `pageSizeLabel` 显示“每页显示：”。
- Datasource 页面保持 `#F6F6F6` 页面底色，筛选、Table 和 Pagination 必须放在同一个白色内容面板中；内容面板不加阴影和额外圆角。
- 筛选只保留 dbType 和 keyword；工具栏顺序固定为“新增数据源 → 数据源类型 → 数据源名称”，新增入口不放在 PageHeader extra。筛选 Input / SelectTrigger 统一使用 Yak UI `outlined` variant，不在页面里覆盖基础 border/background。
- 列表行操作只保留“编辑｜删除”文字操作，中间使用轻量 Divider；操作组在操作列内居中对齐；列表不提供单行 Connection Test，连接测试保留在新增 / 编辑表单内。
- Table 启用受控 `rowSelection`；表头和底部 Checkbox 都只全选当前页，跨页已选 ID 保留；筛选条件变化清空选择，单次最多选择 100 条。
- Table `footer` 左侧承载“批量删除 / 批量测试连通性”，右侧继续使用 Yak UI Pagination；批量删除必须二次确认，批量连接测试直接执行并反馈成功 / 失败数量。
- 新增使用 Yak UI `Modal` 两步 Wizard；Modal Header / Footer 固定，只允许 Body 滚动。第一步选择区使用固定高度，数据少时允许自然留白；提供“全部 / 关系型数据库”分类和搜索。Datasource Item 使用紧凑单行结构，只展示 Icon + 名称，不展示说明文案。第二步配置表单遵循 `FORM_RULES.md` 的 Compact Horizontal Form：Label 左对齐、Control 右侧占满，Input / Select / PasswordInput 统一使用 `small`，字段纵向间距保持紧凑，分组只使用轻量边框与标题。连接配置使用 Host + Port + Database 结构化输入并实时展示 JDBC Preview；高级参数使用轻量 Key/Value 列表。当前只展示 `MYSQL / ORACLE / POSTGRE_SQL`，不引入动态 Provider UI。
- Edit 使用与 Create 相同的 Yak UI `Modal` 和配置内容；不展示可修改的数据库类型控件，通过标题明确当前 Provider，且编辑时禁止修改 `dbType`。
- Create / Edit 的必填标识与错误信息统一使用 Yak UI `FieldLabel required` / `FieldRequiredMark` / `FieldError`；Datasource 只持有字段规则和 i18n message，不在页面重复手写红色星号或错误文本样式。
- Create / Update / Connection Test 共用同一个结构化 `connectionParams` Contract：`host / port / database / username / password / properties`；`dbType` 由外层请求字段负责 Provider 路由，不重复塞进连接对象。
- PostgreSQL Create / Edit 必须使用默认端口 `5432`、`jdbc:postgresql://host:port/database` Preview，并且请求体中不得出现顶层 `schema` 字段。
- 高级参数前端只校验 Key 非空 / 不重复；参数名称 canonicalization、布尔 / 枚举 / 数值语义和 Provider-specific 校验全部由对应后端 Provider 持有。
- CRUD、Batch Operations 和 Connection Test 统一走 `service/datasource`。
- Datasource 请求依赖全局当前 Workspace；页面不得自行拼接 `X-Workspace-Id` 或把 `workspaceId` 加进业务 DTO。
- HTTP transport only through `service/http`。
- Common primitives from `@yak-ops/yak-ui`。
- Backend Contract owner stays in `service/datasource/types.ts`。

## Must Not

- Recreate `editor/` or `hooks/` under Datasource。
- Recreate Summary cards。
- Recreate dynamic form schema / renderer / form runtime。
- Recreate SSH Tunnel UI。
- Recreate Driver Manager / Driver Class configuration UI。
- Recreate `management / model / plugin / connection` directories。
- Recreate `packages/datasource`。
- Call `fetch` directly from Datasource UI。
- Import `service/http` directly from App。
- Reintroduce Ant Design or a second UI framework。
