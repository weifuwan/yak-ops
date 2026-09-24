# Frontend Rules

Scope:
- `yak-ops-ui/apps/**`
- `yak-ops-ui/packages/**`
- `yak-ops-ui/scripts/**`

Depends On:
- `./ARCHITECTURE.md`

Related:
- `./SERVICE_RULES.md`
- `./apps/web/APP_RULES.md`
- `./apps/web/app/datasource/DATASOURCE_RULES.md`
- `./packages/yak-ui/UI_RULES.md`
- `./docs/tooling.md`

## Ownership

```text
apps/web/app/<domain> = product capability
apps/web/service      = backend communication + backend contracts
apps/web/utils        = business-agnostic helpers
apps/web/themes       = theme
apps/web/hooks        = cross-component hooks
apps/web/context      = app-wide context
apps/web/config       = runtime configuration
apps/web/constants    = stable constants
apps/web/assets       = bundled assets
apps/web/public       = raw static assets

packages/yak-ui       = business-agnostic UI primitives
scripts               = executable architecture/tooling checks
```

## Local Cohesion

优先局部内聚，不为“概念完整”而拆文件。

不要因为代码里出现 Management / Model / Plugin / Connection 等名词，就自动创建对应目录。

只有独立行为、明显复用或复杂度足够高时才抽取新的文件或目录。

页面专属的小组件可以留在页面文件；仅被一个父组件使用的简单逻辑不需要额外架构层。

## Must

- 代码先确定 owner，再确定目录。
- 业务 UI / state / presentation 跟随 `app/<domain>`。
- Domain endpoint 与后端 Contract 跟随 `service/<domain>`。
- 依赖方向保持 `app → service → http`。
- 通用 UI Primitive 从 `@yak-ops/yak-ui` 使用。
- HTTP 请求统一经过 `service/http`。
- Component / Page / Hook 不直接调用 `fetch`。
- 跨页面运行时状态放 `context`，访问 Hook 放 `hooks`。
- React state 放在拥有行为的最小边界。
- TypeScript、Oxlint、Oxfmt 和 architecture check 报错从源头修复。

## Must Not

- 重新创建 `apps/web/src`、`apps/web/pages`、`apps/web/shared`、根 `src/public/types/mock`。
- 重新创建 `packages/datasource` 或 `@yak-ops/datasource`。
- Service 反向 import `app/**`。
- App 直接 import `service/http`。
- 为单一概念创建没有独立行为的目录层。
- 新增第二套 HTTP Client。
- 重新引入 `antd`、`@ant-design/icons` 或第二套 UI framework。
- App / Domain 直接 import `@base-ui/react`。
- 为局部状态引入 Zustand / Redux。
- 用 broad lint disable、删除 enforcement 或跳过 formatter 让检查变绿。

## Architecture Gate

```bash
npm run architecture:check
```

合理架构演进必须同步 Architecture / Rules / enforcement。

## Validation

```bash
cd yak-ops-ui
npm run check
npm run build
```
