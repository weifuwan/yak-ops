import type { Field as BaseFieldNS } from "@base-ui/react/field";
import { Field as BaseField } from "@base-ui/react/field";
import { cva, type VariantProps } from "class-variance-authority";
import * as React from "react";

import { cn } from "../cn";

export const textareaVariants = cva(
  [
    "min-h-20 w-full resize-y appearance-none border text-[var(--yak-components-input-text)] outline-none",
    "placeholder:text-[var(--yak-components-input-placeholder)]",
    "transition-[background-color,border-color,color] duration-150",
    "focus:border-[var(--yak-components-input-border-focus)] focus:bg-[var(--yak-components-input-bg-focus)]",
    "aria-[invalid=true]:border-[var(--yak-components-input-border-danger)]",
    "data-invalid:border-[var(--yak-components-input-border-danger)]",
    "disabled:cursor-not-allowed disabled:bg-[var(--yak-components-input-bg-disabled)] disabled:text-[var(--yak-components-input-text-disabled)]",
    "read-only:cursor-default",
    "motion-reduce:transition-none",
  ],
  {
    variants: {
      variant: {
        filled:
          "border-transparent bg-[var(--yak-components-input-bg)] hover:border-[var(--yak-components-input-border-hover)] hover:bg-[var(--yak-components-input-bg-hover)] disabled:border-transparent",
        outlined:
          "border-[var(--yak-components-input-border)] bg-[var(--yak-components-input-bg-focus)] hover:border-[var(--yak-components-input-border-focus)] hover:bg-[var(--yak-components-input-bg-focus)] disabled:border-[var(--yak-components-input-border)]",
      },
      size: {
        small:
          "rounded-[var(--yak-radius-control-small)] px-2.5 py-1.5 text-[length:var(--yak-font-size-control-small)]",
        medium:
          "rounded-[var(--yak-radius-control-medium)] px-3 py-2 text-[length:var(--yak-font-size-control-medium)]",
        large:
          "rounded-[var(--yak-radius-control-large)] px-3.5 py-2.5 text-[length:var(--yak-font-size-control-large)]",
      },
    },
    defaultVariants: {
      variant: "filled",
      size: "medium",
    },
  },
);

type NativeTextareaProps = React.ComponentPropsWithoutRef<"textarea">;

export type TextareaProps = Omit<
  NativeTextareaProps,
  "children" | "className" | "defaultValue" | "onChange" | "size" | "value"
> &
  VariantProps<typeof textareaVariants> & {
    className?: string;
    value?: string | number;
    defaultValue?: string | number;
    onValueChange?: BaseFieldNS.Control.Props["onValueChange"];
  };

export const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { className, defaultValue, onValueChange, size, value, variant, ...props },
  ref,
) {
  return (
    <BaseField.Control
      {...(props as Omit<BaseFieldNS.Control.Props, "render">)}
      ref={ref as React.ForwardedRef<HTMLElement>}
      render={<textarea />}
      value={value}
      defaultValue={defaultValue}
      onValueChange={onValueChange}
      className={cn(textareaVariants({ size, variant }), className)}
    />
  );
});

Textarea.displayName = "Textarea";
