import { Combobox as BaseCombobox } from "@base-ui/react/combobox";
import { createContext, useContext, type ReactNode } from "react";

import { cn } from "../cn";
import { inputVariants, type InputProps } from "../input";

type ComboboxSize = NonNullable<InputProps["size"]>;

const ComboboxSizeContext = createContext<ComboboxSize>("medium");

const comboboxFontSizeClasses: Record<ComboboxSize, string> = {
  small: "text-[length:var(--yak-font-size-control-small)]",
  medium: "text-[length:var(--yak-font-size-control-medium)]",
  large: "text-[length:var(--yak-font-size-control-large)]",
};

export type ComboboxProps<
  Value,
  Multiple extends boolean | undefined = false,
  Item = Value,
> = BaseCombobox.Root.Props<Value, Multiple, Item> & {
  size?: ComboboxSize;
};

export function Combobox<Value, Multiple extends boolean | undefined = false, Item = Value>({
  size = "medium",
  ...props
}: ComboboxProps<Value, Multiple, Item>) {
  return (
    <ComboboxSizeContext.Provider value={size}>
      <BaseCombobox.Root {...props} />
    </ComboboxSizeContext.Provider>
  );
}

export type ComboboxInputProps = Omit<BaseCombobox.Input.Props, "className" | "size"> &
  Pick<InputProps, "className" | "size" | "variant">;

export function ComboboxInput({ className, size, variant, ...props }: ComboboxInputProps) {
  const contextSize = useContext(ComboboxSizeContext);
  const resolvedSize = size ?? contextSize;

  return (
    <BaseCombobox.Input
      {...props}
      className={cn(inputVariants({ size: resolvedSize, variant }), className)}
    />
  );
}

export type ComboboxContentProps = Omit<BaseCombobox.Popup.Props, "children" | "className"> & {
  children: BaseCombobox.List.Props["children"];
  className?: string;
  emptyText?: ReactNode;
  positionerClassName?: string;
  side?: BaseCombobox.Positioner.Props["side"];
  align?: BaseCombobox.Positioner.Props["align"];
  sideOffset?: BaseCombobox.Positioner.Props["sideOffset"];
  alignOffset?: BaseCombobox.Positioner.Props["alignOffset"];
};

export function ComboboxContent({
  align = "start",
  alignOffset = 0,
  children,
  className,
  emptyText,
  positionerClassName,
  side = "bottom",
  sideOffset = 4,
  ...props
}: ComboboxContentProps) {
  const size = useContext(ComboboxSizeContext);

  return (
    <BaseCombobox.Portal>
      <BaseCombobox.Positioner
        side={side}
        align={align}
        sideOffset={sideOffset}
        alignOffset={alignOffset}
        className={cn("z-60 outline-none", positionerClassName)}
      >
        <BaseCombobox.Popup
          {...props}
          className={cn(
            "min-w-[var(--anchor-width)] max-w-80 overflow-hidden rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-select-border)] bg-[var(--yak-components-select-bg)] shadow-[var(--yak-components-select-shadow)] outline-none",
            comboboxFontSizeClasses[size],
            className,
          )}
        >
          {emptyText ? (
            <BaseCombobox.Empty className="px-3 py-5 text-center text-[length:var(--yak-font-size-control-small)] text-[var(--yak-components-muted-text)]">
              {emptyText}
            </BaseCombobox.Empty>
          ) : null}
          <BaseCombobox.List className="max-h-80 overflow-y-auto p-1 outline-none">
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
        "flex min-h-8 cursor-pointer items-center gap-2 rounded-[var(--yak-radius-control-small)] px-2.5 py-1.5 text-[var(--yak-components-select-item-text)] outline-none",
        "data-highlighted:bg-[var(--yak-components-select-item-bg-hover)] data-selected:font-medium",
        "data-disabled:cursor-not-allowed data-disabled:opacity-45",
        className,
      )}
    />
  );
}

export type ComboboxItemIndicatorProps = Omit<
  BaseCombobox.ItemIndicator.Props,
  "children" | "className"
> & {
  className?: string;
};

export function ComboboxItemIndicator({ className, ...props }: ComboboxItemIndicatorProps) {
  return (
    <BaseCombobox.ItemIndicator
      {...props}
      className={cn(
        "ml-auto flex size-4 shrink-0 items-center justify-center text-[var(--yak-components-select-indicator)]",
        className,
      )}
    >
      <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4" fill="none">
        <path
          d="m5.5 10 3 3 6-6"
          stroke="currentColor"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="1.6"
        />
      </svg>
    </BaseCombobox.ItemIndicator>
  );
}

export const ComboboxEmpty = BaseCombobox.Empty;

export type ComboboxEmptyProps = Omit<BaseCombobox.Empty.Props, "className"> & {
  className?: string;
};

export function ComboboxEmptyState({ className, ...props }: ComboboxEmptyProps) {
  return (
    <BaseCombobox.Empty
      {...props}
      className={cn(
        "px-3 py-5 text-center text-[length:var(--yak-font-size-control-small)] text-[var(--yak-components-muted-text)]",
        className,
      )}
    />
  );
}
