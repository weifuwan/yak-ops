# Web Form Rules

Scope:

- `yak-ops-ui/apps/web/app/**`
- Product-level create / edit / configuration forms rendered with Yak UI primitives

Depends On:

- `/yak-ops-ui/FRONTEND_RULES.md`
- `/yak-ops-ui/apps/web/APP_RULES.md`
- `/yak-ops-ui/packages/yak-ui/UI_RULES.md`

## Principle

Yak Ops management forms use Compact Horizontal Form by default.

```text
Label 104px        Control minmax(0, 1fr)
---------------    -----------------------
用户名             [.....................]
密码               [.....................]
备注               [.....................]
```

Yak UI owns Input / Select / Field / Modal primitives. This contract only owns how product forms compose those primitives.

## Default Density

Management create / edit forms default to:

- Label column: `104px`
- Label / Control gap: `12px` through `gap-3`
- Vertical field gap: `10px` through `space-y-2.5`
- Label typography: `text-xs leading-4`
- Label top alignment: `pt-1.5`
- Input / PasswordInput / Select: `size="small"`
- Input / PasswordInput / SelectTrigger: `variant="outlined"`
- Textarea: `size="small" + variant="outlined"`
- Modal footer Button: `size="small"`

Do not use medium controls in a management form without an explicit product reason.

## Horizontal Field

Default field structure:

```tsx
<Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3" invalid={Boolean(error)}>
  <FieldLabel required htmlFor="field-id" className="pt-1.5 text-xs leading-4">
    字段
  </FieldLabel>

  <div className="min-w-0">
    <Input id="field-id" size="small" variant="outlined" />
    <FieldError match={Boolean(error)} className="mt-1">
      {error}
    </FieldError>
  </div>
</Field>
```

The Label / Control grid belongs to the product form. Do not add a second FormItem / FormRow component system only to wrap this layout.

## Validation

Required marker and validation presentation must use Yak UI:

- `FieldLabel required`
- `FieldRequiredMark`
- `FieldError`
- native `aria-invalid`

Do not handwrite a red `*`, duplicate validation colors, or build a page-local error component.

Validation content belongs to the owning domain form; validation presentation belongs to Yak UI.

## Multi-control Row

When one field needs multiple controls, keep the outer 104px Label / Control contract unchanged and compose inside the Control column.

Example:

```tsx
<div className="grid grid-cols-[minmax(0,1fr)_146px] gap-2">
  <Input size="small" variant="outlined" />
  <Input size="small" variant="outlined" />
</div>
```

Host + Port and similar structures must not redefine the whole form grid.

## Textarea

Simple management descriptions / remarks stay compact:

```tsx
<Textarea size="small" variant="outlined" rows={2} className="min-h-[56px] resize-none" />
```

Textarea uses the Yak UI native visual contract; pages do not recreate its border or focus treatment.

## Modal Form

Create / Edit:

```text
Modal
→ compact horizontal fields
→ small footer buttons
```

Destructive confirmation:

```text
Dialog
```

Compact management forms may use `bodyClassName="py-3"` to align with the Datasource form density.

Short, stable-height create / edit forms may opt into `<Modal centered />` when the content comfortably fits the viewport without normal body scrolling.

Use `centered` for:

- simple compact forms with a small, predictable number of fields
- short configuration forms whose height remains stable across normal validation states

Keep the default top-offset Modal for:

- long forms
- multi-step Wizards
- Table / search-result content
- dynamic content that may grow significantly
- forms that commonly need body scrolling

Do not choose `centered` only for visual preference; content height and interaction stability decide the placement.

## Selection / Search Modal

A modal whose main job is selecting records may contain Table or search results, but its form controls still follow this contract:

- search Input is `small + outlined`
- role / type Select is `small + outlined`
- field rows use the same 104px Label / Control layout when labels are shown
- result Table is not forced into a form-row layout

## Must

- use Compact Horizontal Form for ordinary management create / edit forms.
- use Yak UI Field / Input / PasswordInput / Select / Textarea / Button / Modal / Dialog.
- keep Label width stable at 104px across ordinary forms.
- keep Control content inside `minmax(0, 1fr)`.
- keep error text under the Control column.
- prefer existing Yak UI tokens and variants over page-specific border/focus styling.
- keep forms locally owned by their Domain.

## Must Not

- use vertical Label-above-Control layout as the default management form.
- introduce page-specific Label widths without a real layout reason.
- use medium controls only because they are the component default.
- handwrite required markers or validation presentation.
- create another Form runtime / schema / FormItem abstraction.
- import Base UI directly from App code.
- add Ant Design or another UI framework.

## Current Baseline

Datasource Create / Edit is the visual baseline for Compact Horizontal Form.

Management User / Workspace forms are expected to follow the same density and alignment while keeping their own domain validation and behavior.
