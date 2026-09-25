import { Combobox as BaseCombobox } from "@base-ui/react/combobox";
import type { ReactNode } from "react";

import { cn } from "../cn";

export type ComboboxProps<Value> = BaseCombobox.Root.Props<Value>;

export function Combobox<Value>(props: ComboboxProps<Value>) {
  return <BaseCombobox.Root {...props} />;
}

export type ComboboxInputProps = Omit<BaseCombobox.Input.Props, "className"> & {
  className?: string;
};

export function ComboboxInput({ className, ...props }: ComboboxInputProps) {
  return (
    <BaseCombobox.Input
      className={cn(
        "h-9 w-full rounded-[10px] border border-transparent bg-[var(--yak-components-input-bg)] px-3 text-[13px] text-[var(--yak-components-input-text)] outline-none",
        "placeholder:text-[var(--yak-components-input-placeholder)] hover:border-[var(--yak-components-input-border-hover)] hover:bg-[var(--yak-components-input-bg-hover)]",
        "focus:border-[var(--yak-components-input-border-focus)] focus:bg-[var(--yak-components-input-bg-focus)] focus:ring-[3px] focus:ring-[var(--yak-components-input-focus-ring)]",
        "disabled:cursor-not-allowed disabled:opacity-45",
        className,
      )}
      {...props}
    />
  );
}

export type ComboboxContentProps = Omit<BaseCombobox.Popup.Props, "children" | "className"> & {
  children: ReactNode;
  className?: string;
  side?: BaseCombobox.Positioner.Props["side"];
  align?: BaseCombobox.Positioner.Props["align"];
  sideOffset?: BaseCombobox.Positioner.Props["sideOffset"];
};

export function ComboboxContent({
  align = "start",
  children,
  className,
  side = "bottom",
  sideOffset = 4,
  ...props
}: ComboboxContentProps) {
  return (
    <BaseCombobox.Portal>
      <BaseCombobox.Positioner
        side={side}
        align={align}
        sideOffset={sideOffset}
        className="z-60 outline-none"
      >
        <BaseCombobox.Popup
          {...props}
          className={cn(
            "min-w-[var(--anchor-width)] max-w-80 overflow-hidden rounded-xl border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] shadow-[var(--yak-components-panel-shadow)] outline-none",
            className,
          )}
        >
          <BaseCombobox.List className="max-h-72 overflow-y-auto p-1 outline-none">
            {children}
          </BaseCombobox.List>
        </BaseCombobox.Popup>
      </BaseCombobox.Positioner>
    </BaseCombobox.Portal>
  );
}

export type ComboboxItemProps<Value = unknown> = Omit<
  BaseCombobox.Item.Props,
  "className" | "value"
> & {
  className?: string;
  value?: Value;
};

export function ComboboxItem<Value = unknown>({ className, ...props }: ComboboxItemProps<Value>) {
  return (
    <BaseCombobox.Item
      {...props}
      className={cn(
        "flex min-h-8 cursor-pointer items-center gap-2 rounded-lg px-2.5 py-1.5 text-[13px] text-[var(--yak-components-panel-text)] outline-none",
        "data-highlighted:bg-[var(--yak-components-menu-item-hover)] data-selected:font-medium",
        "data-disabled:cursor-not-allowed data-disabled:opacity-45",
        className,
      )}
    />
  );
}

export const ComboboxEmpty = BaseCombobox.Empty;

export type ComboboxEmptyProps = Omit<BaseCombobox.Empty.Props, "className"> & {
  className?: string;
};

export function ComboboxEmptyState({ className, ...props }: ComboboxEmptyProps) {
  return (
    <BaseCombobox.Empty
      className={cn(
        "px-3 py-5 text-center text-xs text-[var(--yak-components-muted-text)]",
        className,
      )}
      {...props}
    />
  );
}
