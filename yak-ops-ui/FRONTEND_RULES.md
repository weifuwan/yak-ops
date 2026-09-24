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

## Must

- 代码先确定 owner，再确定目录。
- 业务 UI / state / presentation 跟随 `app/<domain>`。
- Domain endpoint 与后端 Contract 跟随 `service/<domain>`。
- 依赖方向保持 `app → service → http`。
- 通用 UI Primitive 从 `@yak-ops/yak-ui` 使用。
- HTTP 请求统一经过 `service/http`。
- Component / Page / Hook 不直接调用 `fetch`。
- 跨页面运行时状态放 `context`，访问 Hook 放 `hooks`。
- Theme 使用 `themes` 提供的语义 Token。
- URL 已表达的状态归 URL；后端事实以 API 返回值为准。
- React state 放在拥有行为的最小边界。
- TypeScript、Oxlint、Oxfmt 和 architecture check 报错从源头修复。

## Must Not

- 重新创建 `apps/web/src`、`apps/web/pages`、`apps/web/shared`、根 `src/public/types/mock`。
- 重新创建 `packages/datasource` 或 `@yak-ops/datasource`。
- Service 反向 import `app/**`。
- App 直接 import `service/http`。
- 把 Datasource / Login 私有 helper 丢进全局 `utils/types/hooks/constants`。
- 新增第二套 HTTP Client。
- 重新引入 `antd`、`@ant-design/icons` 或第二套 UI framework。
- App / Domain 直接 import `@base-ui/react`。
- 为局部状态引入 Zustand / Redux。
- 用 broad lint disable、删除 enforcement 或跳过 formatter 让检查变绿。

## Architecture Gate

```bash
npm run architecture:check
```

如果新架构是合理演进：

1. 先更新 Architecture / Rules。
2. 再更新 `check-architecture.mjs`。
3. 最后修改业务代码。

不要先让检查失效，再补解释。

## Validation

```bash
cd yak-ops-ui
npm run check
npm run build
```
