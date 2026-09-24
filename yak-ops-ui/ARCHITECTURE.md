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

PR1 只迁移 ownership，不改变 Datasource 用户行为。

因此 `src/**` 暂时保留以下旧实现：

```text
src/pages/data-source
src/service/datasource
src/service/http
src/shared/lib
src/shared/ui/index.ts
src/pages/login/assets
src/app/styles/fonts
```

其中：

- `packages/datasource/src/index.tsx` 是 App 唯一 Datasource 入口，暂时桥接 `src/pages/data-source`。
- `src/shared/ui/index.ts` 是旧 Datasource 的兼容入口，只 re-export `packages/yak-ui`。
- Login 视频与字体二进制暂留旧路径，App 通过静态 import 使用。

这些是 migration bridge，不是长期 owner。后续 PR 必须逐步删除，而不是继续往 `src/**` 增加新业务代码。

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

Migration bridge 中旧 Datasource 仍依赖 `src/service/**` 与 `src/shared/**`，只允许在迁移期间存在。

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

PR1 只建立 public package boundary；后续重构目标为：

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

`packages/yak-ui` 拥有无业务语义的 UI Primitive：

```text
Button
Input
Select
...
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

PR2 补齐 Yak UI AntD replacement set，但仍不迁移 Datasource 业务实现，也不删除 AntD dependency。

后续阶段负责：

- Datasource 内部 capability 重构。
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
