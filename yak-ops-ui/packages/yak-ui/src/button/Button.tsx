import { Button as BaseButton } from "@base-ui/react/button";
import type { Button as BaseButtonNS } from "@base-ui/react/button";
import { forwardRef, type ForwardedRef } from "react";

import { cn } from "../cn";
import { Spinner } from "../spinner";

export type ButtonVariant =
  | "primary"
  | "secondary"
  | "tertiary"
  | "outline"
  | "ghost"
  | "danger"
  | "danger-soft";

export type ButtonSize = "sm" | "md" | "lg";

export interface ButtonVariants {
  variant?: ButtonVariant;
  size?: ButtonSize;
  isIconOnly?: boolean;
  fullWidth?: boolean;
}

export function buttonVariants({
  variant = "primary",
  size = "md",
  isIconOnly = false,
  fullWidth = false,
}: ButtonVariants = {}) {
  return cn(
    "button",
    `button--${variant}`,
    `button--${size}`,
    isIconOnly && "button--icon-only",
    fullWidth && "button--full-width",
  );
}

export type ButtonProps = Omit<
  BaseButtonNS.Props,
  "className" | "disabled" | "focusableWhenDisabled" | "ref"
> &
  ButtonVariants & {
    className?: string;
    isDisabled?: boolean;
    isPending?: boolean;
  };

export const ButtonRoot = forwardRef<HTMLButtonElement, ButtonProps>(function ButtonRoot(
  {
    children,
    className,
    fullWidth,
    isDisabled,
    isIconOnly,
    isPending = false,
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
      data-slot="button"
      data-pending={isPending ? "true" : undefined}
      disabled={Boolean(isDisabled || isPending)}
      focusableWhenDisabled={isPending && !isDisabled}
      aria-busy={isPending || undefined}
      className={cn(buttonVariants({ fullWidth, isIconOnly, size, variant }), className)}
    >
      {isPending ? (
        <>
          <span className="inline-flex items-center gap-2 opacity-0">{children}</span>
          <span className="pointer-events-none absolute inset-0 flex items-center justify-center" aria-hidden="true">
            <Spinner size={size === "lg" ? "large" : size === "sm" ? "small" : "medium"} className="text-current" />
          </span>
        </>
      ) : (
        children
      )}
    </BaseButton>
  );
});

ButtonRoot.displayName = "Button";
export const Button = Object.assign(ButtonRoot, { Root: ButtonRoot });
