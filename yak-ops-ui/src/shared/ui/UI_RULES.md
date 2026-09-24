# Yak UI Rules

Scope:
- `yak-ops-ui/src/shared/ui/**`

Depends On:
- `../../../FRONTEND_RULES.md`
- `../../../ARCHITECTURE.md`

Owns:
- Yak Ops 可复用 UI Primitive
- Primitive 的公开 Props Contract
- Primitive keyboard / focus / disabled / accessibility 行为
- Yak UI Design Token 与视觉状态

Public Import:
- `@/shared/ui`

## Dependency Direction

```text
Page / Feature / App
        ↓
@/shared/ui
        ↓
@base-ui/react
        ↓
DOM
```

`@base-ui/react` 是 Yak UI 的实现依赖，不是业务层 API。

## Must

- Primitive 必须无 Login / Datasource 等业务语义。
- Page / Feature / App 只能从 `@/shared/ui` 使用公共 Yak UI Primitive。
- 需要 Headless interaction 时优先由 `@base-ui/react` 提供底层行为，Yak UI 自己拥有公开 API 与视觉 Contract。
- Yak UI 不直接向业务层 re-export Base UI 原始 Primitive。
- 样式统一使用 Tailwind 与 Yak UI Design Token。
- Variant Contract 使用 `class-variance-authority` 管理。
- Button 默认 `type="button"`。
- Button 第一版只提供 `primary / secondary / ghost / danger` 四种视觉意图。
- Button 第一版只提供 `small / medium / large` 三种尺寸。
- Button loading 必须阻止重复触发，同时保留明确的 busy 状态。
- Input 第一版只提供 `small / medium / large` 三种尺寸；invalid 状态使用 `aria-invalid` / Base UI Field state，不新增第二套错误状态事实来源。
- Input 不拥有 Label、Description、Error Message、Prefix / Suffix 等组合能力；这些出现真实复用需求后再建立独立边界。
- Select 使用组合式 Primitive：`Select / SelectTrigger / SelectValue / SelectContent / SelectItem`。
- Select 的 keyboard navigation、focus restore、popup interaction、selection semantics 交给 Base UI；Yak UI 只拥有公开组合 Contract 与视觉。
- Select 不新增把 `options / searchable / clearable / renderOption` 等便利能力堆在一起的超级组件 API；selection mode 由 Root Contract 表达，其它能力优先通过组合扩展。
- `className` 只作为布局、定位和必要的局部 escape hatch，不用于重新发明 Primitive 的核心视觉状态。
- 只有真实、稳定、重复使用的 UI Boundary 才新增 Primitive。

## Must Not

- 在 Page / Feature / App 直接导入 `@base-ui/react`。
- 在 Shared UI 中请求 API、读取业务 Service 或拥有业务状态。
- 把 Datasource、Login、Project、Workflow 等业务概念写进 Primitive。
- 为未来需求预创建大量空组件。
- 用一个超级组件通过几十个 Props 覆盖所有场景。
- 为了迁移方便继续新增 Ant Design 通用 Primitive 封装。

## Adoption

- 当前产品 Button 已统一迁移到 Yak UI；不要重新创建 Page 私有 Button wrapper。
- 普通文本 Input 与普通 Select 在 Contract 能无损覆盖时迁移到 Yak UI。
- 依赖 Ant Design 专属组合能力的复杂控件暂时保留原实现，直到对应 Yak UI Primitive 有真实需求。
- Adoption 以“不丢现有用户行为”为前提，不为了去依赖强行降级 searchable、clearable、password、textarea、number、upload 等能力。

## Current Scope

当前 Yak UI Foundation 已包含：

```text
Yak UI
├── Button
├── Input
└── Select
```

这一层只解决通用 Primitive。Form / Field、Checkbox、Switch、Dialog 等能力继续由真实需求驱动，不提前扩展。

## Boundary

`shared/ui` 是 Yak Ops 内部的 Yak UI。

当前不单独建立 npm workspace / published package；只有出现跨应用复用或独立发布需求时，再评估包级拆分。
