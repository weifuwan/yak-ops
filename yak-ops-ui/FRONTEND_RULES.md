# Frontend Rules

Scope:
- `yak-ops-ui/apps/**`
- `yak-ops-ui/packages/**`

Depends On:
- `./ARCHITECTURE.md`

Related:
- `./SERVICE_RULES.md`
- `./apps/web/APP_RULES.md`
- `./apps/web/app/datasource/DATASOURCE_RULES.md`
- `./packages/yak-ui/UI_RULES.md`

## Ownership

```text
apps/web/app/<domain> = product capability
apps/web/service      = backend communication
apps/web/utils        = business-agnostic helpers
apps/web/themes       = theme
apps/web/hooks        = cross-component hooks
apps/web/context      = app-wide context
apps/web/config       = runtime configuration
apps/web/constants    = stable constants
apps/web/assets       = bundled assets
apps/web/public       = raw static assets

packages/yak-ui       = business-agnostic UI primitives
```

## Must

- 代码先确定 owner，再确定目录。
- 业务 UI / state / model 跟随 `app/<domain>`。
- Domain endpoint 跟随 `service/<domain>`。
- 通用 UI Primitive 从 `@yak-ops/yak-ui` 使用。
- HTTP 请求统一经过 `service/http`。
- Component / Page / Hook 不直接调用 `fetch`。
- 跨页面运行时状态放 `context`，访问 Hook 放 `hooks`。
- Theme 使用 `themes` 提供的语义 Token。
- URL 已表达的状态归 URL；后端事实以 API 返回值为准。
- React state 放在拥有行为的最小边界。
- 可直接计算的值直接派生。
- Effect 只处理 React 外部同步、订阅和生命周期。
- TypeScript、Oxlint、Oxfmt 报错从源头修复。

## Must Not

- 重新创建 `apps/web/src`、`apps/web/pages`、根 `src`、根 `public`。
- 重新创建 `packages/datasource` 或 `@yak-ops/datasource`。
- 把 Datasource / Login 私有 helper 丢进全局 `utils/types/hooks/constants`。
- 新增第二套 HTTP Client。
- 重新引入 `antd`、`@ant-design/icons` 或第二套 UI framework。
- App / Domain 直接 import `@base-ui/react`。
- 为局部状态引入 Zustand / Redux。
- 为简单逻辑增加 interface / impl / adapter 等 Java 风格层级。
- 用 broad lint disable 或跳过 formatter 让检查变绿。

## Domain Rule

```text
app/datasource
= UI + state + model + domain interaction

service/datasource
= backend endpoint + request/response adaptation
```

相同规则适用于后续 Domain：不要为了“目录对称”提前创建空目录，只在真实能力出现时建立 owner。

## Validation

```bash
cd yak-ops-ui
npm run check
npm run build
```
