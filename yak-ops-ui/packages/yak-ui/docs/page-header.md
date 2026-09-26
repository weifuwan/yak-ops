# PageHeader Design

Status: Design only  
Package: `@yak-ops/yak-ui`  
Implementation: Not started

## Goal

Provide one business-agnostic page heading primitive for Yak Ops pages.

`PageHeader` standardizes the common page-top structure:

- title
- optional description
- optional right-side extra content
- optional bottom border

The component owns presentation and layout only. Product pages continue to own business actions, routing, data fetching and page-specific state.

## Ownership

Planned source location:

```text
yak-ops-ui/packages/yak-ui/src/page-header/
├── PageHeader.tsx
└── index.ts
```

Planned public import:

```tsx
import { PageHeader } from "@yak-ops/yak-ui";
```

`PageHeader` belongs to Yak UI because it has no Datasource, Task or other product semantics and is expected to be reused across product pages.

It must not live under:

- `apps/web/app/layout`: this area owns application shell layout such as TopBar / Sidebar.
- `apps/web/app/<domain>`: this would make a generic page primitive domain-owned.
- a new shared/components layer: Yak UI is already the shared UI boundary.

## V1 Contract

Planned public Props:

```ts
export interface PageHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  extra?: ReactNode;
  bordered?: boolean;
  className?: string;
}
```

### title

Required page title.

- Main visual emphasis of the header.
- Intended to represent the current page.
- V1 renders it with page-heading semantics.
- Product code owns the actual copy and localization.

### description

Optional supporting text below the title.

- Used for short explanatory copy.
- Must not become a second content area.
- Long help content belongs in the page body.

### extra

Optional right-side composition slot.

Typical content:

- primary create button
- one or more lightweight page actions
- business-specific controls composed by the product page

`PageHeader` does not interpret, reorder or create actions inside `extra`.

### bordered

Optional bottom divider.

Default:

```ts
bordered = false
```

Use `bordered` when the header needs a strong visual boundary from the page body, such as a DataWorks-style title bar.

Without `bordered`, the header behaves as a clean page heading block.

### className

Layout / positioning escape hatch only.

It must not become a parallel visual API. Product code should not depend on internal PageHeader DOM structure or override internal title / description styles through fragile selectors.

## Layout Contract

Conceptual structure:

```text
PageHeader
┌──────────────────────────────────────────────────────────┐
│ title                                      extra          │
│ description                                               │
└──────────────────────────────────────────────────────────┘
 optional bottom border
```

Rules:

- Left content owns the remaining horizontal space.
- `extra` stays on the right and does not overlap the title block.
- When `description` is absent, the title remains vertically balanced.
- When `extra` is absent, no empty right-side placeholder is rendered.
- Border belongs to the PageHeader root and spans the header width.
- PageHeader does not own the outer page content width.
- PageHeader does not own page-body spacing after itself; the parent page layout decides the next section spacing.

## Responsive Behavior

V1 should remain simple and predictable:

- normal desktop width: title block left, `extra` right
- constrained width: content may wrap instead of shrinking title text into an unusable width
- `extra` remains product-owned composition
- PageHeader must not add mobile-specific business behavior

Exact responsive CSS is an implementation detail, but title and actions must remain readable and operable at narrow widths.

## Visual Contract

PageHeader should use Yak UI design tokens and Tailwind utilities rather than product-specific values.

Visual hierarchy:

```text
title        = primary text / page-heading emphasis
description  = secondary muted text
extra        = normal Yak UI composition
border       = standard subtle divider token
background   = transparent by default
```

V1 does not introduce page-specific background colors.

The component should visually support both current reference styles:

### Title-only / bordered

```tsx
<PageHeader title="数据源列表" bordered />
```

Expected structure:

```text
数据源列表
────────────────────────────────────────
```

### Title + description + extra

```tsx
<PageHeader
  title="AI定时任务"
  description="按计划自动执行AI任务，也可随时手动触发。"
  extra={<Button>+ 新建定时任务</Button>}
/>
```

Expected structure:

```text
AI定时任务                                + 新建定时任务
按计划自动执行AI任务，也可随时手动触发。
```

### Title + extra + bordered

```tsx
<PageHeader
  title="数据源列表"
  extra={<Button>新增数据源</Button>}
  bordered
/>
```

### Title + description + bordered

```tsx
<PageHeader
  title="任务管理"
  description="查看和管理任务运行情况。"
  bordered
/>
```

These four combinations define the initial V1 need.

## Accessibility

- The page title must use real heading semantics instead of a styled `div`.
- `extra` keeps the semantics and keyboard behavior of its child controls.
- PageHeader itself must not add unnecessary ARIA roles.
- Decorative border has no semantic meaning.
- The component must not create focusable wrappers.

## Must

- Stay business-agnostic.
- Keep the API small and composition-first.
- Support title-only usage without artificial placeholders.
- Support optional description.
- Support arbitrary React composition in `extra`.
- Support optional bottom border.
- Use Yak UI tokens / visual language.
- Keep product actions outside PageHeader implementation.

## Must Not

V1 must not add:

- breadcrumb
- back button / routing behavior
- tabs
- loading state
- sticky behavior
- page data fetching
- authorization logic
- action permission filtering
- title editing
- built-in Button props
- built-in action arrays
- fixed page width
- domain-specific variants
- AntD PageHeader compatibility props

If one of these becomes a repeated real requirement, extend the contract from an actual usage case rather than predicting it now.

## Relationship to App Layout

```text
App shell
├── TopBar
├── Sidebar
└── Page content
    ├── PageHeader
    └── Product content
```

`TopBar` answers "where am I in the application shell?"

`PageHeader` answers "what page am I looking at and what page-level actions are available?"

They are different ownership boundaries and should not be merged.

## Planned Acceptance Criteria

Implementation can start only when there is a real page migration/use case.

When implemented, acceptance should verify:

- title-only rendering
- title + description rendering
- title + extra rendering
- bordered and non-bordered rendering
- no placeholder DOM for omitted optional slots
- heading semantics
- narrow-width wrapping
- `className` support
- public export from `@yak-ops/yak-ui`
- no product-domain dependency
- no change to product behavior

## Deferred

No source component is added as part of this design change.

The design document intentionally comes first. A later implementation change should build the primitive from this contract and introduce the first real product usage separately or in the same implementation PR only when explicitly planned.
