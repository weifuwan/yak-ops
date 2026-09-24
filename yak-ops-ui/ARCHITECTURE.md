# Yak Ops UI Architecture

Status: Active

Scope:
- `yak-ops-ui/apps/**`
- `yak-ops-ui/packages/**`
- PR1 migration bridge under `yak-ops-ui/src/**`

Depends On:
- `../ARCHITECTURE.md`
- `./FRONTEND_RULES.md`

## Principle

Yak Ops UI 使用 App + Product Package + UI Package 组织前端。

```text
apps/web
   ↓
packages/datasource
   ↓
packages/yak-ui
```

App 负责产品组装，Product Package 负责业务能力，Yak UI 负责无业务语义的 Primitive。

目录不是为了“看起来像 Monorepo”。每个 workspace 必须有真实 owner。

## Stable Structure

```text
yak-ops-ui/
├── apps/
│   └── web/
│       ├── APP_RULES.md
│       ├── package.json
│       └── src/
│           ├── app/
│           │   ├── App.tsx
│           │   ├── layout/
│           │   ├── providers/
│           │   ├── router/
│           │   └── styles/
│           ├── pages/
│           │   └── login/
│           ├── service/
│           │   └── auth/
│           └── main.tsx
├── packages/
│   ├── datasource/
│   │   ├── DATASOURCE_UI_RULES.md
│   │   ├── package.json
│   │   └── src/
│   │       ├── api/
│   │       ├── connection/
│   │       ├── editor/
│   │       ├── i18n/
│   │       ├── management/
│   │       ├── model/
│   │       ├── plugin/
│   │       └── index.tsx
│   └── yak-ui/
│       ├── UI_RULES.md
│       ├── package.json
│       └── src/
│           ├── button/
│           ├── input/
│           ├── select/
│           ├── cn.ts
│           ├── index.ts
│           └── styles.css
├── src/
├── index.html
├── package.json
├── tsconfig.json
└── vite.config.ts
```

## Migration Bridge

Datasource 产品代码已全部进入 `packages/datasource`。

`src/**` 当前只保留少量跨阶段 infrastructure / binary asset：

```text
src/service/http
src/shared/lib
src/pages/login/assets
src/app/styles/fonts
```

`src/pages/data-source`、`src/service/datasource`、`src/shared/ui` 已删除，禁止重新创建。

HTTP transport 是否独立成 package，由后续真实跨 package owner 决定；不要为了目录对称提前拆。

## Dependency Direction

正式依赖方向：

```text
apps/web
   ├────────────→ packages/yak-ui
   ↓
packages/datasource
   ↓
packages/yak-ui
   ↓
@base-ui/react
```

禁止：

```text
packages/yak-ui   ✕→ packages/datasource
packages/yak-ui   ✕→ apps/web
packages/datasource ✕→ apps/web
packages/datasource ✕→ Router / AppLayout / AuthProvider
```

Datasource API 当前只依赖 `src/service/http` 这一条 infrastructure bridge；Datasource 业务实现不再依赖旧 Page / Service 目录。

## apps/web

`apps/web` 拥有浏览器应用组装：

- React mount。
- Router。
- 应用级 Provider。
- App Layout。
- Login。
- 全局样式。
- 应用级认证 Service。

App 回答“产品如何组合”，不回答“Datasource 如何实现”。

Datasource 路由固定通过：

```ts
import { DataSourcePage } from "@yak-ops/datasource"
```

App 禁止直接 import `src/pages/data-source`。

详细规则见 `apps/web/APP_RULES.md`。

## packages/datasource

`packages/datasource` 是 Datasource 前端唯一产品 owner。

当前结构为：

```text
packages/datasource/src/
├── api/
├── model/
├── management/
├── editor/
├── connection/
├── plugin/
└── index.ts
```

目录按 capability owner 划分，不建立全局 `components/hooks/utils/types` 大桶。

详细规则见 `packages/datasource/DATASOURCE_UI_RULES.md`。

## packages/yak-ui

`packages/yak-ui` 拥有无业务语义的 UI Primitive。Ant Design 已移除，产品代码统一通过 Yak UI / 原生浏览器能力实现通用交互：

```text
Button / Input / PasswordInput / Textarea
NumberField / Switch / Select / Combobox
DropdownMenu / Tabs / Collapsible
Tooltip / Popover / Dialog / Drawer
Pagination / Spinner / Empty / Badge / Toast
```

固定依赖：

```text
product code
    ↓
@yak-ops/yak-ui
    ↓
@base-ui/react
```

Yak UI 自己拥有 Props Contract、Design Token 和视觉状态。

详细规则见 `packages/yak-ui/UI_RULES.md`。

## Service Boundary

当前 HTTP transport 暂留 `src/service/http`。

长期方向：

- App 专属认证调用归 `apps/web`。
- Datasource API 归 `packages/datasource/api`。
- 通用 HTTP transport 如果形成稳定跨 package owner，再独立定义 shared infrastructure package。

禁止把 HTTP transport 塞进 Yak UI。

## State Boundary

状态按事实来源归属：

- URL 状态归 Router。
- 应用级认证状态归 App Provider。
- Datasource 业务事实归 Datasource package。
- Primitive interaction state 归 Yak UI。
- 后端事实以 API 响应为准。

不要复制同一事实来源形成第二份 state。

## Asset Boundary

- App 全局样式 → `apps/web/src/app/styles`。
- Yak UI Design Token → `packages/yak-ui/src/styles.css`。
- Product package 私有资源 → 对应 package。
- Login 视频 / App 字体当前暂留 migration bridge，后续单独搬迁二进制资源。

## Package Manager

Yak Ops UI 继续使用 npm。

根 `package.json` 使用 npm workspaces：

```text
apps/*
packages/*
```

PR1 不切换 pnpm / yarn，避免把 package-manager migration 与 architecture migration 混在一起。

## Current Migration Stage

PR1 已完成 Workspace / ownership 建立。

PR2 已补齐 Yak UI AntD replacement set。

PR3 已完成 Datasource package capability 重构：

```text
management / editor / connection / plugin / model / api
```

下一阶段负责：

- 使用 Yak UI replacement set 迁移存量 AntD 组件。
- 删除 Ant Design / @ant-design/icons / legacy less overrides。

当前仍不引入：

- pnpm / Turborepo。
- Zustand / Redux。
- 新产品页面。

## Verification

完整前端验证：

```bash
cd yak-ops-ui
npm run check
npm run build
```

验证范围必须覆盖：

```text
apps
packages
src migration bridge
vite.config.ts
```

工具定义见 `docs/tooling.md`。
