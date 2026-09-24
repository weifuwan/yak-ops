# Frontend Rules

Scope:
- `yak-ops-ui/apps/**`
- `yak-ops-ui/packages/**`
- remaining shared infrastructure under `yak-ops-ui/src/**`

Depends On:
- `./ARCHITECTURE.md`

Related:
- `./docs/tooling.md`
- `./apps/web/APP_RULES.md`
- `./packages/yak-ui/UI_RULES.md`
- `./packages/datasource/DATASOURCE_UI_RULES.md`

## Ownership

```text
apps/web
= browser product composition

packages/datasource
= Datasource product capability

packages/yak-ui
= business-agnostic UI primitives

src/service/http + src/shared/lib
= remaining shared infrastructure only
```

## Must

- 代码先确定 owner，再确定目录。
- App 只组合产品能力；Datasource 业务实现归 `packages/datasource`。
- 通用 UI Primitive 统一从 `@yak-ops/yak-ui` 使用。
- Datasource 路由只通过 `@yak-ops/datasource` 进入 App。
- Page / Product Package / App 不直接依赖 `@base-ui/react`。
- `src/pages/data-source`、`src/service/datasource`、`src/shared/ui` 已删除；禁止重新创建。
- 优先原生 HTML 语义，保留 keyboard / focus / disabled 行为。
- 可点击元素必须有明确 pointer cursor。
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

- 重新引入 `antd`、`@ant-design/icons`、Ant Design CSS override 或 AntD compatibility wrapper。
- 引入 MUI / Chakra / Mantine 等第二套 UI framework。
- `apps/web` 绕过 `@yak-ops/datasource` 直接依赖 Datasource 内部 capability。
- `packages/datasource` 依赖 `apps/web`。
- `packages/yak-ui` 依赖任何业务 package。
- App / Datasource 直接 import `@base-ui/react`。
- 在 Datasource 内重新创建 Button / Input / Select / Dialog 等通用 Primitive。
- 新增 Umi Max、Umi Router、Umi Model。
- 在 Component / Hook 直接调用 `fetch`。
- 新增第二套 HTTP Client。
- 为简单前端逻辑增加 interface / impl / adapter 等 Java 风格层级。
- 为未来能力预创建空 package、Provider、Store 或菜单。
- 为局部状态引入 Zustand / Redux。
- 用 `useEffect + setState` 维护可直接派生的数据副本。
- 为了文件长度拆 owner。
- 用 broad lint disable、跳过 formatter 或关闭 warning 让检查变绿。

## Package Boundary

```text
浏览器应用组装 / 路由 / Provider / Login
→ apps/web

Datasource 产品能力
→ packages/datasource

无业务语义 UI Primitive
→ packages/yak-ui

纯通用 infrastructure
→ 只有形成真实跨 package owner 后再单独定义
```

不要把 `components/hooks/utils/types` 当作顶层架构。

## Datasource Package Boundary

Datasource 内部按 capability 拆：

```text
management
editor
connection
plugin
model
api
```

组件、Hook、类型和 helper 跟随 owner 放置。

Datasource 动态表单状态由 `editor/formRuntime.tsx` 拥有；Yak UI 不拥有 Datasource Schema、字段联动、业务校验或 payload 组装。

## Styling Boundary

- Tailwind 4 是样式基础设施。
- Yak UI Token 归 `packages/yak-ui/src/styles.css`。
- App 全局 reset / font / viewport 归 `apps/web/src/app/styles`。
- Product 私有视觉归对应 package。
- 不使用全局位置选择器改写业务 package 内部结构。
- 不使用 Less 作为新的样式入口。
- 通用交互组件优先 Yak UI；业务上传使用原生 file input + owning package logic。
- Icon 统一优先使用 Lucide 或能力自身已有 SVG，不引入第二套 icon framework。

## Validation

```bash
cd yak-ops-ui
npm run check
npm run build
```

`check` 必须覆盖 `apps / packages / src`。

## Boundary

全局规则只定义跨 workspace 稳定约束。

App、Yak UI、Datasource 使用各自最近的 RULES。
