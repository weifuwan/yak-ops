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

```text
apps/web
├── app
├── public
├── service
├── utils
├── themes
├── types
├── hooks
├── context
├── config
├── constants
└── assets

packages/yak-ui
packages/datasource
```

`apps/web` 本身就是浏览器应用根，不再额外套 `src/`。

根目录基础设施只接收跨业务能力；业务组件、业务 Hook、业务类型跟随业务 owner。真正无业务语义的 UI Primitive 继续归 `packages/yak-ui`。

## Web Root Ownership

```text
app        → 应用组装、Router、Layout
pages      → PR2 前存量页面入口
service    → HTTP transport + 应用级后端访问
utils      → 无业务工具
themes     → Theme Token / light / dark
types      → 跨 Web 稳定类型
hooks      → 跨组件 React Hook
context    → App-wide Context
config     → 运行配置
constants  → 稳定常量
assets     → 参与构建的资源
public     → 原样静态资源
```

禁止重新创建 `apps/web/src`、根 `yak-ops-ui/src` 或根 `yak-ops-ui/public`。

## Stable Structure

```text
yak-ops-ui/
├── apps/
│   └── web/
│       ├── app/
│       ├── pages/
│       ├── service/
│       │   ├── auth/
│       │   └── http/
│       ├── utils/
│       ├── themes/
│       ├── types/
│       ├── hooks/
│       ├── context/
│       ├── config/
│       ├── constants/
│       ├── assets/
│       ├── public/
│       ├── index.html
│       ├── main.tsx
│       └── vite.config.ts
├── packages/
│   ├── datasource/
│   └── yak-ui/
├── package.json
└── tsconfig.json
```

## Dependency Direction

当前正式方向：

```text
apps/web
   ├────────────→ packages/yak-ui
   ↓
packages/datasource
   ↓
packages/yak-ui
```

PR1 到 PR2 之间仅允许一条临时 bridge：

```text
packages/datasource/api
        ↓
apps/web/service/http
```

这是为了删除历史根 `src/service/http`。PR2 将 Datasource 迁回 `app/datasource` 后，这条 bridge 必须消失。

除这条 transport bridge 外，Datasource 不得依赖 App Router、Layout、Context 或页面。

## Theme Architecture

Theme 是 Web 基础设施，不属于 Yak UI 或某个业务页面。

```text
themes/
├── tokens.css
├── light.css
├── dark.css
└── index.css
```

职责：

- `tokens.css`：字体、圆角、阴影等主题无关 Token。
- `light.css`：Light 语义颜色。
- `dark.css`：Dark 语义颜色及必要组件变量覆盖。
- `context/theme-context.tsx`：运行时 Theme 状态与 DOM 同步。
- `hooks/use-theme.ts`：组件访问 Theme 的入口。
- `types/theme.ts`：Theme contract。

默认主题保持 Light，避免 PR1 在未完成业务颜色迁移前改变现有视觉。Theme Runtime 已支持 `light / dark / system`。

## Service Boundary

`apps/web/service/http` 是当前唯一 HTTP transport owner。

`apps/web/service/auth` 拥有 Login / Logout / Current User。

Datasource endpoint 仍由 `packages/datasource/src/api` 拥有；PR2 会随 Datasource 一起迁移到 Web Domain。

Component / Page / Hook 禁止直接调用 `fetch`。

## Asset Boundary

- 原样静态文件 → `apps/web/public`。
- 字体、视频等构建资源 → `apps/web/assets`。
- App 全局样式 → `apps/web/app/styles`。
- Theme → `apps/web/themes`。
- Yak UI Primitive 样式 → `packages/yak-ui/src/styles.css`。

## Vite Boundary

Vite Root 固定为 `apps/web`。

根 workspace 只负责 npm workspace、TypeScript 和统一命令入口：

```bash
npm run dev
npm run check
npm run build
```

Build 仍输出到 `yak-ops-ui/dist`，不改变现有部署产物位置。

## Current Migration Stage

PR1 — Web Foundation + Theme Architecture：

- Web Root 去掉 `src/` 中间层。
- 删除根 `src/**` migration bridge。
- 静态资源收口到 `apps/web/public` / `assets`。
- HTTP / notification 基础设施收口到 Web Root。
- 建立 Theme Token + Light/Dark + Theme Context。
- Vite Root 收口到 `apps/web`。

PR2 才负责：

- `packages/datasource` → `apps/web/app/datasource`。
- Login 进一步按 App Domain 收口。
- 删除 Datasource → Web HTTP 临时 bridge。

## Verification

```bash
cd yak-ops-ui
npm run check
npm run build
```
