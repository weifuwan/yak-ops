# Modal Design

Status: Implemented  
Package: `@yak-ops/yak-ui`  
Implementation: `yak-ops-ui/packages/yak-ui/src/modal`

## Goal

Provide the shared modal shell used by product flows that need a title, scrollable content, a stable footer and standard close behavior.

Modal is a layout primitive. It must not contain Datasource, Login or other product semantics.

## Contract

```tsx
<Modal
  open={open}
  onClose={() => setOpen(false)}
  title="新增数据源"
  width={1080}
  footer={
    <>
      <Button>取消</Button>
      <Button variant="primary">完成</Button>
    </>
  }
>
  {content}
</Modal>
```

Core props:

- `open`: controlled visibility.
- `onClose`: called when the close button, Escape key or outside press requests close.
- `title`: accessible modal title.
- `width`: number or CSS width value; defaults to `640`.
- `footer`: optional fixed footer content.
- `className / headerClassName / bodyClassName / footerClassName`: layout escape hatches only.
- `style`: popup-level style escape hatch when a real layout requirement cannot be expressed by `width`.

## Layout

```text
┌──────────────────────────────┐
│ title                      × │
├──────────────────────────────┤
│                              │
│        scrollable body       │
│                              │
├──────────────────────────────┤
│                       footer │
└──────────────────────────────┘
```

- The popup starts at a fixed `1rem` viewport offset and remains horizontally centered; avoid vertical `translateY(-50%)` centering because it can place the popup on half-pixel coordinates and make 1px child borders render like 2px lines.
- The popup never grows beyond the viewport and keeps the matching `1rem` bottom clearance through its max-height.
- Only the body scrolls.
- Header and footer remain visible while the body scrolls.
- Footer is omitted entirely when `footer` is not provided.

## Interaction

Base UI Dialog owns focus trapping, focus restoration, Escape handling and outside-press behavior.

The Yak UI Modal owns the shared visual shell and exposes the product-facing `open + onClose` contract.

## Boundary

Modal owns:

- popup shell
- backdrop
- title
- close button
- body scrolling
- fixed footer
- width

Product code owns:

- step or wizard state
- forms and validation
- submit lifecycle
- loading / disabled policy
- business-specific copy and actions

Do not turn Modal into an AntD compatibility layer or add business-specific convenience props.
