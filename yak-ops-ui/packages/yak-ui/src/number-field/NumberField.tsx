import { NumberField as BaseNumberField } from "@base-ui/react/number-field";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "../cn";

export const NumberField = BaseNumberField.Root;
export type NumberFieldProps = BaseNumberField.Root.Props;

const groupVariants = cva(
  [
    "group/number-field flex w-full min-w-0 items-stretch overflow-hidden border border-transparent bg-[var(--yak-components-input-bg)] text-[var(--yak-components-input-text)] outline-none",
    "hover:border-[var(--yak-components-input-border-hover)] hover:bg-[var(--yak-components-input-bg-hover)]",
    "data-focused:border-[var(--yak-components-input-border-focus)] data-focused:bg-[var(--yak-components-input-bg-focus)] data-focused:ring-[3px] data-focused:ring-[var(--yak-components-input-focus-ring)]",
    "data-invalid:border-[var(--yak-components-input-border-danger)]",
    "data-disabled:cursor-not-allowed data-disabled:opacity-45",
  ],
  {
    variants: {
      size: {
        small: "h-7 rounded-lg",
        medium: "h-9 rounded-[10px]",
        large: "h-10 rounded-[11px]",
      },
    },
    defaultVariants: { size: "medium" },
  },
);

export type NumberFieldGroupProps = Omit<BaseNumberField.Group.Props, "className"> &
  VariantProps<typeof groupVariants> & { className?: string };

export function NumberFieldGroup({ className, size, ...props }: NumberFieldGroupProps) {
  return <BaseNumberField.Group className={cn(groupVariants({ size }), className)} {...props} />;
}

export type NumberFieldInputProps = Omit<BaseNumberField.Input.Props, "className"> & {
  className?: string;
};

export function NumberFieldInput({ className, ...props }: NumberFieldInputProps) {
  return (
    <BaseNumberField.Input
      className={cn(
        "w-0 min-w-0 flex-1 appearance-none border-0 bg-transparent px-3 text-[13px] outline-none placeholder:text-[var(--yak-components-input-placeholder)]",
        className,
      )}
      {...props}
    />
  );
}

const controlClass =
  "flex w-7 items-center justify-center border-l border-[var(--yak-components-control-border)] text-[var(--yak-components-input-icon)] outline-none hover:bg-[var(--yak-components-input-bg-hover)] focus-visible:bg-[var(--yak-components-input-bg-hover)] disabled:cursor-not-allowed disabled:opacity-40";

export type NumberFieldIncrementProps = Omit<BaseNumberField.Increment.Props, "className"> & { className?: string };
export function NumberFieldIncrement({ className, children, ...props }: NumberFieldIncrementProps) {
  return (
    <BaseNumberField.Increment aria-label={props["aria-label"] ?? "Increment value"} className={cn(controlClass, className)} {...props}>
      {children ?? <span aria-hidden="true">+</span>}
    </BaseNumberField.Increment>
  );
}

export type NumberFieldDecrementProps = Omit<BaseNumberField.Decrement.Props, "className"> & { className?: string };
export function NumberFieldDecrement({ className, children, ...props }: NumberFieldDecrementProps) {
  return (
    <BaseNumberField.Decrement aria-label={props["aria-label"] ?? "Decrement value"} className={cn(controlClass, className)} {...props}>
      {children ?? <span aria-hidden="true">−</span>}
    </BaseNumberField.Decrement>
  );
}
