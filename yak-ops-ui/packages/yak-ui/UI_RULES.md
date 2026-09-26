# Yak UI Rules

Scope:

- `yak-ops-ui/packages/yak-ui/**`

Depends On:

- `../../FRONTEND_RULES.md`
- `../../ARCHITECTURE.md`

Owns:

- Yak Ops business-agnostic UI Primitive
- Primitive public Props Contract
- keyboard / focus / disabled / accessibility behavior
- Yak UI Design Token and visual state

Public Import:

- `@yak-ops/yak-ui`

## Dependency Direction

```text
apps / product packages
        ↓
@yak-ops/yak-ui
        ↓
@base-ui/react
        ↓
DOM
```

Base UI is an implementation dependency, not a product-facing API.

## Must

- Primitive must not contain Login / Datasource or other product semantics.
- App and product packages use public Yak UI exports instead of importing Base UI directly.
- Headless interaction / accessibility should come from Base UI when it already owns the behavior.
- Yak UI owns stable Props, composition API, Design Token and visual states.
- Tailwind + Yak UI tokens are the styling foundation.
- `--yak-color-primary` is the shared primary accent token; current baseline is `#0033FF`. Component active / focus colors should reference it instead of duplicating blue literals.
- Button / Input / SelectTrigger share control-radius tokens instead of hard-coded radius values: small `6px`, medium `8px`, large `10px`. Product code should not redefine these control radii through `className`.
- Variant contracts use `class-variance-authority` when variants are real product-wide concepts.
- Button defaults to `type="button"`.
- Button `primary` uses `--yak-color-primary` as its source color; hover / active are derived from that token rather than maintaining a separate dark primary palette.
- Input / Textarea / NumberField use one shared input visual language.
- Input and SelectTrigger expose `filled` as the default surface and `outlined` for explicit white/surface controls with a visible shared border token; product code must use the variant instead of fighting `border-transparent` through `className`. Input focus and SelectTrigger focus/open use the primary border only; they do not add a focus box-shadow / ring.
- Select / Menu / Tooltip / Popover / Dialog / Drawer / Tabs remain compositional instead of becoming giant convenience-prop components.
- Select separates domain values from user-visible labels: when `value` and `label` differ, product code must pass `items` to `Select` so `SelectValue` renders the matching label. Controlled state and `onValueChange` continue to use the domain `value`; product code must not duplicate value-to-label lookup logic inside the trigger.
- Modal is the shared product-facing dialog shell: it owns title, close affordance, scrollable body, fixed footer and width; product code owns business content, step state and submit lifecycle.
- Modal Popup uses a fast fade + subtle upward motion: open starts about 12px below and settles in about 140ms with a decelerating curve; close moves about 8px upward and fades out in about 160ms with a smooth standard curve. Backdrop fades in about 180ms and fades out about 160ms so the surface and overlay finish together. Modal never uses scale, bounce or spring motion, and product code must not override Modal motion through `className`.
- Drawer motion is enabled by default; product flows that intentionally need immediate open / close use the explicit `animated={false}` opt-out instead of overriding transition classes through `className`.
- Dialog / Modal / Drawer / Popover / Menu popup interaction, focus restore, Escape and outside press behavior stay in Base UI.
- Toast is the common replacement for message / notification feedback.
- Badge is the common lightweight status-label primitive; product-specific status semantics stay outside Yak UI.
- Table owns generic tabular rendering, loading / empty presentation, scroll / sticky header and pagination placement; product code owns fetching, filters, mutations and business cell content.
- PageHeader owns generic page title, description, right-side composition and optional divider; product code owns page actions and business behavior.
- `className` is a layout / positioning / necessary escape hatch, not a second visual contract.

## Form Boundary

Yak UI Form / Field only own:

- semantic form container
- label / description / error presentation
- required-field marker presentation through `FieldLabel required` / `FieldRequiredMark`
- accessibility relationship
- control visual state

Yak UI does not own:

- Datasource form schema
- field dependency rules
- dynamic visibility
- cross-field business validation
- form list business data
- API payload assembly
- submit lifecycle

Product form state belongs to the owning product package. Product validation messages stay in the product i18n layer and are passed to `FieldError`; Yak UI must not own Datasource-specific required copy or validation rules.

Do not rebuild an AntD-style mega Form API inside Yak UI.

## Table Boundary

Yak UI Table uses an AntD-familiar core contract without becoming an AntD compatibility layer:

- `columns / dataSource / rowKey` define generic tabular data.
- Column `render` owns presentation composition but not product data fetching.
- `pagination` reuses Yak UI Pagination; Table does not implement a second pagination control.
- When `pagination.total` is omitted, Table may paginate the supplied in-memory `dataSource`.
- When `pagination.total` is provided, Table treats `dataSource` as the already-paged server result.
- `loading` keeps the current table structure mounted and overlays a Spinner instead of replacing the table.
- `rowSelection` is a generic controlled / uncontrolled selection contract implemented by injecting a selection column; Table Body does not hard-code Checkbox behavior.
- `footer` is a generic left-side composition slot sharing the bottom row with Pagination; batch / CRUD semantics remain product-owned.
- Select-all only affects selectable rows on the currently rendered page and preserves selected keys from other pages.
- Disabled row selection comes from `getCheckboxProps`; selection never contains Datasource or other domain semantics.
- Sorter is a single-column AntD-familiar contract: comparator function enables local sorting; `sorter: true` exposes sort state for server-side handling without reordering local rows.
- Filter state is per-column: `onFilter` enables local filtering; columns with `filters` but no `onFilter` expose controlled filter state for server-side handling.
- Controlled `sortOrder / filteredValue` and uncontrolled `defaultSortOrder / defaultFilteredValue` are both supported.
- Table-level `onChange(pagination, filters, sorter, extra)` is the single generic notification boundary for paginate / sort / filter changes.
- Local filter and sort run before local pagination; server pagination remains owned by the product when `pagination.total` is provided.
- Current Table supports size, border, row hover, selected-row state, ellipsis, sort, filter, horizontal / vertical scroll and sticky header.
- Table header uses the shared `#F2F2F2` surface and does not draw internal header dividers; `bordered` applies the outer frame and body grid only.
- Pagination is a single-line compact control; the active page uses `--yak-color-primary` instead of Button primary styling, and page-size wording is supplied through the generic `pageSizeLabel` composition point.
- Checked Table selection controls use the shared primary token; product code must not invent a separate selection blue.
- Expandable rows, fixed columns, virtualization, multi-column sort and component overrides remain deferred.

Table must not own:

- request / API lifecycle
- search form or filter schema
- CRUD actions
- Datasource-specific columns
- backend pagination contract adaptation

## Upload Boundary

Yak UI does not provide an Upload product component.

File selection uses native browser file input. Upload request, file type / size policy, progress, retry and backend contract belong to the owning product package.

## Legacy Replacement Reference

```text
AntD Button          → Button
AntD Checkbox        → Checkbox
AntD Input           → Input
AntD Input.Password  → PasswordInput
AntD Input.TextArea  → Textarea
AntD InputNumber     → NumberField
AntD Form            → Form + Field presentation; product owns form state
AntD Select          → Select
AntD Switch          → Switch
AntD Tooltip         → Tooltip
AntD Popover         → Popover
AntD Modal           → Modal
AntD Drawer          → Drawer
AntD Tabs            → Tabs
AntD Dropdown        → DropdownMenu
AntD Pagination      → Pagination
AntD Table           → Table
AntD Spin            → Spinner
AntD Empty           → Empty
AntD Collapse        → Collapsible
AntD Tag             → Badge
AntD message         → Toast
AntD notification    → Toast
AntD Upload          → native file input + product upload logic
AntD Space           → normal flex / grid layout
```

## Must Not

- App / product packages import `@base-ui/react` directly.
- Yak UI requests APIs or reads product services.
- Primitive names or Props expose Datasource-specific concepts.
- Yak UI re-exports raw Base UI components as its public contract without an intentional Yak UI boundary.
- Add Ant Design or a second UI framework such as MUI / Chakra inside Yak UI.
- Recreate AntD-compatible APIs just to make migration search-and-replace easier.
- Put request, search-form, CRUD or domain-specific behavior inside Table.
- Add product-specific filter controls or backend query assembly to Table sort / filter hooks.
- Add future primitives that have no real current migration or product need.

## Current Set

```text
Yak UI
├── Badge
├── Button
├── Checkbox
├── Collapsible
├── Combobox
├── Dialog
├── Drawer
├── DropdownMenu
├── Empty
├── Field
├── Form
├── Input
├── Modal
├── PasswordInput
├── NumberField
├── PageHeader
├── Pagination
├── Popover
├── Select
├── Spinner
├── Switch
├── Table
├── Tabs
├── Textarea
├── Toast
└── Tooltip
```

This set is the current Yak Ops UI foundation. New primitives remain problem-driven.

## Boundary

`packages/yak-ui` is the internal Yak Ops UI package.

Cross-app publication is not a current requirement. Do not introduce a separate external publishing workflow until a real consumer exists.
