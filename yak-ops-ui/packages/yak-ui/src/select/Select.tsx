import { createContext, useContext, type ReactNode } from "react";
import { Select as BaseSelect } from "@base-ui/react/select";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "../cn";

type SelectSize = "small" | "medium" | "large";

const SelectSizeContext = createContext<SelectSize>("medium");

const selectFontSizeClasses: Record<SelectSize, string> = {
  small: "text-[var(--yak-font-size-control-small)]",
  medium: "text-[var(--yak-font-size-control-medium)]",
  large: "text-[var(--yak-font-size-control-large)]",
};

export type SelectProps<
  Value,
  Multiple extends boolean | undefined = false,
> = Omit<BaseSelect.Root.Props<Value, Multiple>, "size"> & {
  size?: SelectSize;
};

export function Select<Value, Multiple extends boolean | undefined = false>({
  size = "medium",
  ...props
}: SelectProps<Value, Multiple>) {
  return (
    <SelectSizeContext.Provider value={size}>
      <BaseSelect.Root {...props} />
    </SelectSizeContext.Provider>
  );
}

const selectTriggerVariants = cva(
  [
    "group/select-trigger flex w-full cursor-pointer items-center border text-left text-[var(--yak-components-input-text)] outline-none",
    "transition-[background-color,border-color,color] duration-150",
    "focus-visible:border-[var(--yak-components-input-border-focus)] focus-visible:bg-[var(--yak-components-input-bg-focus)]",
    "data-popup-open:border-[var(--yak-components-input-border-focus)] data-popup-open:bg-[var(--yak-components-input-bg-focus)]",
    "data-placeholder:text-[var(--yak-components-input-placeholder)]",
    "data-disabled:cursor-not-allowed data-disabled:bg-[var(--yak-components-input-bg-disabled)] data-disabled:text-[var(--yak-components-input-text-disabled)]",
    "motion-reduce:transition-none",
  ],
  {
    variants: {
      variant: {
        filled:
          "border-transparent bg-[var(--yak-components-input-bg)] hover:border-[var(--yak-components-input-border-hover)] hover:bg-[var(--yak-components-input-bg-hover)] data-disabled:border-transparent",
        outlined:
          "border-[var(--yak-components-input-border)] bg-[var(--yak-components-input-bg-focus)] hover:border-[var(--yak-components-input-border-focus)] hover:bg-[var(--yak-components-input-bg-focus)] data-disabled:border-[var(--yak-components-input-border)]",
      },
      size: {
        small: "h-7 gap-1.5 rounded-[var(--yak-radius-control-small)] px-2.5 text-[var(--yak-font-size-control-small)]",
        medium: "h-9 gap-2 rounded-[var(--yak-radius-control-medium)] px-3 text-[var(--yak-font-size-control-medium)]",
        large: "h-10 gap-2 rounded-[var(--yak-radius-control-large)] px-3.5 text-[var(--yak-font-size-control-large)]",
      },
    },
    defaultVariants: {
      variant: "filled",
      size: "medium",
    },
  },
);

export type SelectTriggerProps = Omit<BaseSelect.Trigger.Props, "className"> &
  VariantProps<typeof selectTriggerVariants> & {
    className?: string;
  };

export function SelectTrigger({
  children,
  className,
  size,
  variant,
  ...props
}: SelectTriggerProps) {
  const contextSize = useContext(SelectSizeContext);
  const resolvedSize = size ?? contextSize;

  return (
    <BaseSelect.Trigger
      {...props}
      className={cn(selectTriggerVariants({ size: resolvedSize, variant }), className)}
    >
      <span className="min-w-0 flex-1 truncate">{children}</span>
      <BaseSelect.Icon className="shrink-0 text-[var(--yak-components-input-icon)] transition-transform duration-150 group-data-popup-open/select-trigger:rotate-180 motion-reduce:transition-none">
        <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4" fill="none">
          <path
            d="m6 8 4 4 4-4"
            stroke="currentColor"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="1.5"
          />
        </svg>
      </BaseSelect.Icon>
    </BaseSelect.Trigger>
  );
}

export type SelectValueProps = Omit<BaseSelect.Value.Props, "className"> & {
  className?: string;
};

export function SelectValue({ className, ...props }: SelectValueProps) {
  return <BaseSelect.Value {...props} className={cn("min-w-0 truncate", className)} />;
}

export type SelectContentProps = Omit<BaseSelect.Popup.Props, "children" | "className"> & {
  children: ReactNode;
  className?: string;
  positionerClassName?: string;
  side?: BaseSelect.Positioner.Props["side"];
  align?: BaseSelect.Positioner.Props["align"];
  sideOffset?: BaseSelect.Positioner.Props["sideOffset"];
  alignOffset?: BaseSelect.Positioner.Props["alignOffset"];
};

export function SelectContent({
  align = "start",
  alignOffset = 0,
  children,
  className,
  positionerClassName,
  side = "bottom",
  sideOffset = 4,
  ...props
}: SelectContentProps) {
  const size = useContext(SelectSizeContext);

  return (
    <BaseSelect.Portal>
      <BaseSelect.Positioner
        side={side}
        align={align}
        sideOffset={sideOffset}
        alignOffset={alignOffset}
        alignItemWithTrigger={false}
        className={cn("z-50 outline-none", positionerClassName)}
      >
        <BaseSelect.Popup
          {...props}
          className={cn(
            "min-w-[var(--anchor-width)] max-w-80 overflow-hidden rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-select-border)] bg-[var(--yak-components-select-bg)] shadow-[var(--yak-components-select-shadow)] outline-none",
            selectFontSizeClasses[size],
            className,
          )}
        >
          <BaseSelect.List className="max-h-80 overflow-y-auto p-1 outline-none">
            {children}
          </BaseSelect.List>
        </BaseSelect.Popup>
      </BaseSelect.Positioner>
    </BaseSelect.Portal>
  );
}

export type SelectItemProps<Value = unknown> = Omit<
  BaseSelect.Item.Props,
  "className" | "value"
> & {
  className?: string;
  value?: Value;
};

export function SelectItem<Value = unknown>({ className, ...props }: SelectItemProps<Value>) {
  return (
    <BaseSelect.Item
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

export type SelectItemTextProps = Omit<BaseSelect.ItemText.Props, "className"> & {
  className?: string;
};

export function SelectItemText({ className, ...props }: SelectItemTextProps) {
  return <BaseSelect.ItemText {...props} className={cn("min-w-0 flex-1 truncate", className)} />;
}

export type SelectItemIndicatorProps = Omit<
  BaseSelect.ItemIndicator.Props,
  "children" | "className"
> & {
  className?: string;
};

export function SelectItemIndicator({ className, ...props }: SelectItemIndicatorProps) {
  return (
    <BaseSelect.ItemIndicator
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
    </BaseSelect.ItemIndicator>
  );
}

export { selectTriggerVariants };
