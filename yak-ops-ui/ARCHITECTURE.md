# Yak Ops UI Architecture

Status: Active

Scope:
- `yak-ops-ui/apps/**`
- `yak-ops-ui/packages/**`

Depends On:
- `../ARCHITECTURE.md`
- `./FRONTEND_RULES.md`

## Principle

Yak Ops UI 使用 Workspace + Dify-style Web Root。

`apps/web` 是产品 Web Root。

业务能力归 `app/<domain>`，后端通信与后端 Contract 归 `service/<domain>`，真正跨业务的基础能力才进入 root infrastructure。无业务语义 UI Primitive 归 `packages/yak-ui`。

## Domain Locality

Domain 内优先局部内聚，不把概念名自动变成目录层级。

Datasource 当前结构：

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

Datasource 当前就是普通管理页面：

```text
Filter + Table + Pagination + CRUD Drawer
```

`index.tsx` 拥有列表、筛选、分页和删除确认状态；`table.tsx` 只负责列表展示；`form.tsx` 只负责新增、编辑和连接测试。

禁止为了 Datasource CRUD 重新创建 Editor Runtime、Domain Hook、Summary Layer 或动态表单体系。

## Dependency Direction

```text
app/router
   ↓
app/datasource ─────→ packages/yak-ui
   ↓
service/datasource
   ↓
service/http
```

核心 invariant：

```text
app → service → http
```

## Datasource Contract Ownership

稳定后端 Contract 归：

```text
service/datasource/types.ts
```

App 只通过 `app/datasource/types.ts` 重新导出这些 Contract。

当前前端只消费 CRUD、分页和 Connection Test；不消费 Summary、Catalog、Plugin Config、Driver Upload 或 Runtime Install API。

## Datasource Product Baseline

当前只展示：

- MySQL
- Oracle
- PostgreSQL

表单使用结构化 JDBC 连接输入：

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

前端只负责当前 MySQL / Oracle / PostgreSQL 的 Host / Port / Database 输入、JDBC Preview 和轻量 Key/Value 高级参数。HTTP Contract 使用结构化 `connectionParams` 对象；前端不把连接参数序列化成 JSON 字符串。JDBC URL 生成、Driver Class、Provider 差异、Normalize 和 Connection Test 仍由后端 JDBC Plugin 负责；SSH 与动态 Driver Manager 不属于当前前端能力。

## Web Root Ownership

```text
app        → Product Domain + Router + Layout
service    → Domain API + Backend Contract + HTTP transport
utils      → 无业务工具
themes     → Theme
types      → 跨 Web 稳定类型
hooks      → 跨组件 React Hook
context    → App-wide Context
config     → 运行配置
constants  → 稳定常量
assets     → 参与构建的资源
public     → 原样静态资源
```

## App Shell

Authenticated product pages share one application shell:

```text
app/layout/
├── AppLayout.tsx
├── TopBar.tsx
├── ProductSidebar.tsx
├── ProductLauncher.tsx
├── AllProductMenu.tsx
└── navigation.ts
```

Ownership:

- `AppLayout` owns the viewport and the Global Product Launcher open / close state.
- `TopBar` owns product identity, launcher trigger and current-user actions.
- `ProductSidebar` owns navigation inside the current product.
- `ProductLauncher` owns a fixed 220px launcher track. The track, not the first-level panel alone, opens from `translateX(-220px)` to `translateX(0)` in 300ms and closes in 220ms without resizing Sidebar / Outlet.
- `所有产品` is a dedicated `view-all` row. Activating it keeps the first-level panel visible, highlights the row with `#1c1e21`, and opens `AllProductMenu` to its right.
- `AllProductMenu` is absolutely anchored to the track with `left: 100%`, so its left edge always touches the first-level panel's right edge. It clips from 765px to 0 width on close, uses background `#1c1e21`, opens in 240ms and closes in 170ms.
- A full Launcher close is staggered rather than hard-sequenced: the second level starts collapsing immediately, then the shared launcher track starts sliding 36ms later. Because both levels share the same moving coordinate system, no gap may appear between them during close.
- Product/category rows use subtle `#282b2e` hover feedback and brighter text/icon color; neither first nor second level adds an outer shadow.
- `navigation.ts` owns real product entries and category grouping; current data exposes only the existing `数据集成` product.
- Launcher closes from the TopBar X trigger, Escape and route change.
- While Launcher is open, a transparent blank-area interaction layer sits below the menu panels and above page content. A single blank-area click closes the whole Launcher; the visual close still staggers the second level ahead of the first level.
- Product pages rendered inside `AppLayout` fill the available container; they do not subtract shell dimensions from `100vh / 100dvh`.

## Service Boundary

`service/http` 是唯一 HTTP transport owner。

Datasource Service 保持：

```text
service/datasource/
├── index.ts
└── types.ts
```

不按 CRUD endpoint 机械拆文件。

## Architecture Enforcement

```bash
npm run architecture:check
```

架构变化必须同时更新：

```text
ARCHITECTURE.md
*_RULES.md
check-architecture.mjs
```

## Verification

```bash
cd yak-ops-ui
npm run check
npm run build
```
