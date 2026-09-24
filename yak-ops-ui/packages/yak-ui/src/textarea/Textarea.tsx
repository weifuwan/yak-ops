import type { Field as BaseFieldNS } from "@base-ui/react/field";
import { Field as BaseField } from "@base-ui/react/field";
import { cva, type VariantProps } from "class-variance-authority";
import type * as React from "react";

import { cn } from "../cn";

export const textareaVariants = cva(
  [
    "min-h-20 w-full resize-y appearance-none border border-transparent bg-[var(--yak-components-input-bg)] text-[var(--yak-components-input-text)] outline-none",
    "placeholder:text-[var(--yak-components-input-placeholder)]",
    "transition-[background-color,border-color,box-shadow,color] duration-150",
    "hover:border-[var(--yak-components-input-border-hover)] hover:bg-[var(--yak-components-input-bg-hover)]",
    "focus:border-[var(--yak-components-input-border-focus)] focus:bg-[var(--yak-components-input-bg-focus)] focus:ring-[3px] focus:ring-[var(--yak-components-input-focus-ring)]",
    "data-invalid:border-[var(--yak-components-input-border-danger)] data-invalid:focus:ring-[var(--yak-components-input-danger-ring)]",
    "disabled:cursor-not-allowed disabled:border-transparent disabled:bg-[var(--yak-components-input-bg-disabled)] disabled:text-[var(--yak-components-input-text-disabled)]",
    "read-only:cursor-default motion-reduce:transition-none",
  ],
  {
    variants: {
      size: {
        small: "rounded-lg px-2.5 py-1.5 text-xs",
        medium: "rounded-[10px] px-3 py-2 text-[13px]",
        large: "rounded-[11px] px-3.5 py-2.5 text-sm",
      },
    },
    defaultVariants: { size: "medium" },
  },
);

type NativeTextareaProps = React.ComponentPropsWithRef<"textarea">;
type TextareaVariantProps = VariantProps<typeof textareaVariants>;

export type TextareaProps = Omit<
  NativeTextareaProps,
  "children" | "className" | "defaultValue" | "onChange" | "size" | "value"
> &
  TextareaVariantProps & {
    className?: string;
    value?: string | number;
    defaultValue?: string | number;
    onValueChange?: BaseFieldNS.Control.Props["onValueChange"];
  };

export function Textarea({
  className,
  defaultValue,
  onValueChange,
  ref,
  size,
  value,
  ...props
}: TextareaProps) {
  return (
    <BaseField.Control
      {...(props as Omit<BaseFieldNS.Control.Props, "render">)}
      ref={ref}
      render={<textarea />}
      value={value}
      defaultValue={defaultValue}
      onValueChange={onValueChange}
      className={textareaVariants({ size, className: cn(className) })}
    />
  );
}
