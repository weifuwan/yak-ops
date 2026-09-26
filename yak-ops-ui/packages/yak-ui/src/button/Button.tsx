import type { Button as BaseButtonNS } from "@base-ui/react/button";
import { Button as BaseButton } from "@base-ui/react/button";
import { cva, type VariantProps } from "class-variance-authority";
import { forwardRef, type ForwardedRef } from "react";

import { cn } from "../cn";

export const buttonVariants = cva(
  "inline-flex cursor-pointer items-center justify-center whitespace-nowrap font-medium outline-none transition-[background-color,border-color,color,box-shadow,transform] duration-150 focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-button-focus-ring)] data-disabled:cursor-not-allowed data-disabled:opacity-45",
  {
    variants: {
      variant: {
        primary:
          "border border-[var(--yak-components-button-primary-bg)] bg-[var(--yak-components-button-primary-bg)] text-[var(--yak-components-button-primary-text)] hover:border-[var(--yak-components-button-primary-bg-hover)] hover:bg-[var(--yak-components-button-primary-bg-hover)] active:border-[var(--yak-components-button-primary-bg-active)] active:bg-[var(--yak-components-button-primary-bg-active)]",
        secondary:
          "border border-transparent bg-[var(--yak-components-button-secondary-bg)] text-[var(--yak-components-button-secondary-text)] hover:bg-[var(--yak-components-button-secondary-bg-hover)] active:bg-[var(--yak-components-button-secondary-bg-active)]",
        ghost:
          "border border-transparent bg-transparent text-[var(--yak-components-button-ghost-text)] hover:bg-[var(--yak-components-button-ghost-bg-hover)] hover:text-[var(--yak-components-button-secondary-text)] active:bg-[var(--yak-components-button-secondary-bg-active)]",
        danger:
          "border border-transparent bg-[var(--yak-components-button-danger-bg)] text-[var(--yak-components-button-danger-text)] hover:bg-[var(--yak-components-button-danger-bg-hover)] active:bg-[var(--yak-components-button-danger-bg-active)]",
      },
      size: {
        small:
          "h-7 gap-1.5 rounded-[var(--yak-radius-control-small)] px-2.5 text-[var(--yak-font-size-control-small)]",
        medium:
          "h-[34px] gap-[7px] rounded-[var(--yak-radius-control-medium)] px-[13px] text-[var(--yak-font-size-control-medium)]",
        large:
          "h-10 gap-2 rounded-[var(--yak-radius-control-large)] px-4 text-[var(--yak-font-size-control-large)]",
      },
    },
    defaultVariants: {
      variant: "secondary",
      size: "medium",
    },
  },
);

export type ButtonProps = Omit<BaseButtonNS.Props, "className" | "ref"> &
  VariantProps<typeof buttonVariants> & {
    className?: string;
    loading?: boolean;
  };

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    children,
    className,
    disabled,
    focusableWhenDisabled,
    loading = false,
    size,
    type = "button",
    variant,
    ...props
  },
  ref,
) {
  return (
    <BaseButton
      {...props}
      ref={ref as ForwardedRef<HTMLElement>}
      type={type}
      disabled={disabled || loading}
      focusableWhenDisabled={focusableWhenDisabled ?? loading}
      aria-busy={loading || undefined}
      className={cn(buttonVariants({ size, variant }), className)}
    >
      {children}
      {loading ? (
        <span
          aria-hidden="true"
          className="size-3.5 animate-spin rounded-full border-2 border-current border-r-transparent motion-reduce:animate-none"
        />
      ) : null}
    </BaseButton>
  );
});

Button.displayName = "Button";
