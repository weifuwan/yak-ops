import { Autocomplete as BaseAutocomplete } from "@base-ui/react/autocomplete";
import { createContext, useContext, type ReactNode } from "react";

import { cn } from "../cn";
import { inputVariants, type InputProps } from "../input";

type AutocompleteSize = NonNullable<InputProps["size"]>;

const AutocompleteSizeContext = createContext<AutocompleteSize>("medium");

const autocompleteFontSizeClasses: Record<AutocompleteSize, string> = {
  small: "text-[length:var(--yak-font-size-control-small)]",
  medium: "text-[length:var(--yak-font-size-control-medium)]",
  large: "text-[length:var(--yak-font-size-control-large)]",
};

export type AutocompleteProps<ItemValue = string> = BaseAutocomplete.Root.Props<ItemValue> & {
  size?: AutocompleteSize;
};

export function Autocomplete<ItemValue = string>({
  size = "medium",
  ...props
}: AutocompleteProps<ItemValue>) {
  return (
    <AutocompleteSizeContext.Provider value={size}>
      <BaseAutocomplete.Root {...props} />
    </AutocompleteSizeContext.Provider>
  );
}

export type AutocompleteInputProps = Omit<BaseAutocomplete.Input.Props, "className"> &
  Pick<InputProps, "className" | "size" | "variant">;

export function AutocompleteInput({
  className,
  placeholder,
  size,
  variant,
  ...props
}: AutocompleteInputProps) {
  const contextSize = useContext(AutocompleteSizeContext);
  const resolvedSize = size ?? contextSize;

  return (
    <BaseAutocomplete.Input
      {...props}
      data-yak-size={resolvedSize}
      placeholder={variant === "underlined" ? placeholder || " " : placeholder}
      className={cn(inputVariants({ size: resolvedSize, variant }), className)}
    />
  );
}

export type AutocompleteContentProps = Omit<
  BaseAutocomplete.Popup.Props,
  "children" | "className"
> & {
  children: BaseAutocomplete.List.Props["children"];
  className?: string;
  emptyContent?: ReactNode;
  positionerClassName?: string;
  side?: BaseAutocomplete.Positioner.Props["side"];
  align?: BaseAutocomplete.Positioner.Props["align"];
  sideOffset?: BaseAutocomplete.Positioner.Props["sideOffset"];
  alignOffset?: BaseAutocomplete.Positioner.Props["alignOffset"];
};

export function AutocompleteContent({
  align = "start",
  alignOffset = 0,
  children,
  className,
  emptyContent,
  positionerClassName,
  side = "bottom",
  sideOffset = 4,
  ...props
}: AutocompleteContentProps) {
  const size = useContext(AutocompleteSizeContext);

  return (
    <BaseAutocomplete.Portal>
      <BaseAutocomplete.Positioner
        side={side}
        align={align}
        sideOffset={sideOffset}
        alignOffset={alignOffset}
        alignItemWithTrigger={false}
        className={cn("z-50 outline-none", positionerClassName)}
      >
        <BaseAutocomplete.Popup
          {...props}
          className={cn(
            "min-w-[var(--anchor-width)] max-w-80 overflow-hidden rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-select-border)] bg-[var(--yak-components-select-bg)] shadow-[var(--yak-components-select-shadow)] outline-none",
            "yak-overlay-popup yak-overlay-scale-95",
            autocompleteFontSizeClasses[size],
            className,
          )}
        >
          {emptyContent != null ? (
            <BaseAutocomplete.Empty
              className="px-3 py-6 text-center text-[length:var(--yak-font-size-control-small)] text-[var(--yak-components-muted-text)]"
            >
              {emptyContent}
            </BaseAutocomplete.Empty>
          ) : null}
          <BaseAutocomplete.List className="max-h-80 overflow-y-auto p-1 outline-none">
            {children}
          </BaseAutocomplete.List>
        </BaseAutocomplete.Popup>
      </BaseAutocomplete.Positioner>
    </BaseAutocomplete.Portal>
  );
}

export type AutocompleteItemProps<Value = unknown> = Omit<
  BaseAutocomplete.Item.Props,
  "className" | "value"
> & {
  className?: string;
  value?: Value;
};

export function AutocompleteItem<Value = unknown>({
  className,
  ...props
}: AutocompleteItemProps<Value>) {
  return (
    <BaseAutocomplete.Item
      {...props}
      className={cn(
        "flex min-h-8 cursor-pointer items-center gap-2 rounded-[var(--yak-radius-control-small)] px-2.5 py-1.5 text-[var(--yak-components-select-item-text)] outline-none",
        "transition-[background-color] duration-150 data-highlighted:bg-[var(--yak-components-select-item-bg-hover)]",
        "data-disabled:cursor-not-allowed data-disabled:opacity-45 motion-reduce:transition-none",
        className,
      )}
    />
  );
}
