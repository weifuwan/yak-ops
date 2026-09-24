# Frontend Rules

Scope:
- `yak-ops-ui/src/**`
- Yak Ops 浏览器端生产代码

Depends On:
- `./ARCHITECTURE.md`
- 后端接口变化时加载 `./SERVICE_RULES.md`

Related:
- `./docs/tooling.md`

Owns:
- React / TypeScript 全局编码约束
- 浏览器交互基础规则
- 前端 ownership 约束

## Must

- 代码按 ownership 放置，依赖方向遵循 `ARCHITECTURE.md`。
- `src/pages` 只保留 `login` 和 `data-source`。
- Page 只组合页面；Feature 只在存在真实独立 owner 时创建；Shared 只放无业务 owner 的复用能力。
- 通用 UI Primitive 统一由 `src/shared/ui` 的 Yak UI 拥有，业务代码从 `@/shared/ui` 使用。
- Page / Feature / App 不直接依赖 `@base-ui/react`；Base UI 只能作为 Yak UI 的底层实现依赖。
- 优先使用原生 HTML 语义，保留键盘、焦点和 disabled 行为。
- 可点击元素必须有明确 pointer cursor。
- URL 已表达的状态归 URL；后端事实以 Service 返回值为准；交互局部状态才使用 `useState`。
- React state 放在拥有行为的最小边界，不复制同一事实来源。
- 可由 props / state / 现有数据直接计算出的值直接派生。
- `useMemo` 只用于真实计算成本，或引用稳定性确实影响下游行为的场景。
- `useCallback` 只在稳定函数引用对 dependency、memo child 或订阅有实际意义时使用。
- Effect 只处理 React 外部同步、订阅或生命周期。
- Hook dependency 保持完整。
- React 组件定义在模块作用域，不在 render 内动态声明组件。
- 公共组件、Hook、类型优先具名导出。
- `index.ts` 只暴露当前 owner 的公共 API，不创建跨域大 barrel。
- Render 期间不写 ref；ref 同步放到 effect 或事件边界。
- TypeScript、Oxlint、Oxfmt 报错从源头修复。
- 修改完成后执行与改动匹配的显式验证。

## Must Not

- 使用 Umi Max、Umi Router 或 Umi Model。
- 在 Page / Component / Hook 直接调用 `fetch`。
- 在 Page / Feature / App 直接导入 `@base-ui/react`，绕过 Yak UI Contract。
- 新增第二套 HTTP 请求工具。
- 为简单前端逻辑增加 interface / impl / adapter 等 Java 风格层级。
- 为未来能力预创建空 Feature、菜单、路由、Provider 或 Store。
- 为页面局部状态引入 Zustand / Redux。
- 用 `useEffect + setState` 维护可直接派生的数据副本。
- 普通计算默认套 `useMemo`。
- 普通事件函数默认套 `useCallback`。
- 为了文件长度拆 owner。
- 把 Datasource 私有组件提前提升到 Shared。
- 用 broad lint disable、关闭 warning 或跳过 formatter 让检查变绿。
- 把 Tailwind class、内部 state、ref、effect 当成产品 Contract。
- 重新创建已删除的产品页面。

## Component Boundary

判断组件放哪里：

```text
只服务一个 Page
→ Page 内

属于一个明确 Datasource 能力，可独立复用
→ Feature 内

无业务语义，被多个真实 owner 使用
→ shared/ui
```

不要因为“以后可能复用”提升组件层级。

## Hook Boundary

Hook 必须拥有真实行为。

适合 Hook：

- 外部订阅生命周期。
- 请求生命周期。
- 一组必须共同演进的交互状态。
- 多个组件共享同一行为 Contract。

不适合 Hook：

- 只包装一个事件函数。
- 只为了减少组件行数。
- 只返回可以直接计算的值。

## Type Boundary

- API Contract 类型归 Service。
- Feature 行为类型归 Feature。
- Page 私有展示类型归 Page。
- 无业务语义的基础类型才进入 Shared。
- 禁止重新引入 Umi 生成的全局 `API.*` 作为新 Contract。

## Styling Boundary

- Tailwind 4 是当前样式基础设施。
- 页面 / Feature 视觉样式归真实 owner。
- 全局 CSS 只负责 reset、字体、主题和 viewport。
- 不使用全局位置选择器修改某个页面内部结构。
- Ant Design 当前允许作为迁移期 UI 依赖；已有业务组件按独立 PR 迁移，不在 Yak UI Foundation PR 中大范围替换。
- 新增通用 Primitive 不再基于 Ant Design 二次封装；优先进入 `shared/ui`，复杂交互可由 Base UI 提供 Headless 行为。

## Validation

完整前端验证：

```bash
cd yak-ops-ui
npm run check
npm run build
```

具体工具职责见 `docs/tooling.md`。

## Boundary

全局规则只定义跨模块稳定约束。

业务规则放在能力 owner，Service 规则放在 `SERVICE_RULES.md`。
