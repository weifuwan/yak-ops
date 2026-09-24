# Frontend Rules

Scope:
- `yak-ops-ui/apps/**`
- `yak-ops-ui/packages/**`

Depends On:
- `./ARCHITECTURE.md`

Related:
- `./SERVICE_RULES.md`
- `./apps/web/APP_RULES.md`
- `./packages/yak-ui/UI_RULES.md`
- `./packages/datasource/DATASOURCE_UI_RULES.md`

## Ownership

```text
apps/web/app        = browser product composition
apps/web/service    = backend communication
apps/web/utils      = business-agnostic helpers
apps/web/themes     = theme tokens + light/dark
apps/web/hooks      = cross-component React hooks
apps/web/context    = app-wide context
apps/web/config     = runtime configuration
apps/web/constants  = stable constants
apps/web/assets     = bundled assets
apps/web/public     = raw static assets

packages/yak-ui     = business-agnostic UI primitives
packages/datasource = temporary Datasource owner until PR2
```

## Must

- 代码先确定 owner，再确定目录。
- Web Root 不再使用 `src/` 中间层。
- 根目录基础设施只接收跨业务能力。
- 业务组件、业务 Hook、业务类型跟随对应 Domain。
- 通用 UI Primitive 统一从 `@yak-ops/yak-ui` 使用。
- Theme 颜色优先使用 `themes` 提供的语义 Token。
- HTTP 请求统一经过 `apps/web/service/http`。
- Component / Page / Hook 不直接调用 `fetch`。
- URL 已表达的状态归 URL；后端事实以 API 返回值为准；局部交互状态才使用 React state。
- React state 放在拥有行为的最小边界，不复制同一事实来源。
- 可由 props / state / 已有数据直接计算的值直接派生。
- `useMemo` / `useCallback` 只在真实成本或引用稳定性有意义时使用。
- Effect 只处理 React 外部同步、订阅和生命周期。
- Hook dependency 保持完整。
- React 组件定义在模块作用域。
- 公共组件、Hook、类型优先具名导出。
- TypeScript、Oxlint、Oxfmt 报错从源头修复。

## Must Not

- 重新创建 `apps/web/src`、根 `src`、根 `public`。
- 把 Datasource / Login 等业务 helper 丢进全局 `utils/types/hooks/constants`。
- 重新引入 `antd`、`@ant-design/icons` 或第二套 UI framework。
- App / Datasource 直接 import `@base-ui/react`。
- 在 Datasource 内重新创建 Button / Input / Select / Dialog 等通用 Primitive。
- 新增 Umi Max、Umi Router、Umi Model。
- 新增第二套 HTTP Client。
- 为简单前端逻辑增加 interface / impl / adapter 等 Java 风格层级。
- 为局部状态引入 Zustand / Redux。
- 用 `useEffect + setState` 维护可直接派生的数据副本。
- 用 broad lint disable、跳过 formatter 或关闭 warning 让检查变绿。

## Root Infrastructure Rule

判断一个能力能否进入 Web Root，只问一个问题：

> 它是否需要知道某个具体业务 Domain？

不知道 Datasource / Login 等业务概念，且被多个区域复用，才适合进入 `utils/hooks/types/constants`。

知道具体业务，就跟随 Domain。

## Theme Rule

```text
themes/tokens.css → structural tokens
themes/light.css  → light semantic colors
themes/dark.css   → dark semantic colors
context/theme-context.tsx → runtime state
hooks/use-theme.ts → component access
```

组件不得创建第二套全局 Theme Provider。

## Validation

```bash
cd yak-ops-ui
npm run check
npm run build
```
