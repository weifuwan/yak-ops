import { Checkbox as BaseCheckbox } from "@base-ui/react/checkbox";
import type { ComponentProps } from "react";

import { cn } from "../cn";

export type CheckboxProps = Omit<ComponentProps<typeof BaseCheckbox.Root>, "className"> & {
  className?: string;
};

export function Checkbox({ className, ...props }: CheckboxProps) {
  return (
    <BaseCheckbox.Root
      {...props}
      className={cn(
        "inline-flex size-4 shrink-0 cursor-pointer items-center justify-center rounded-[3px] border border-[var(--yak-components-checkbox-border)] bg-[var(--yak-components-checkbox-bg)] text-white outline-none transition-[background-color,border-color,box-shadow]",
        "hover:border-[var(--yak-components-checkbox-border-hover)] focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-checkbox-focus-ring)]",
        "data-checked:border-[var(--yak-components-checkbox-bg-checked)] data-checked:bg-[var(--yak-components-checkbox-bg-checked)]",
        "data-indeterminate:border-[var(--yak-components-checkbox-bg-checked)] data-indeterminate:bg-[var(--yak-components-checkbox-bg-checked)]",
        "data-disabled:cursor-not-allowed data-disabled:opacity-45",
        className,
      )}
    >
      <BaseCheckbox.Indicator
        keepMounted
        className="flex size-full items-center justify-center opacity-0 data-checked:opacity-100 data-indeterminate:opacity-100 data-indeterminate:[&_.yak-checkbox-check]:hidden data-indeterminate:[&_.yak-checkbox-indeterminate]:block"
      >
        <svg
          aria-hidden="true"
          viewBox="0 0 16 16"
          className="yak-checkbox-check size-3"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="m3 8.5 3 3 7-7" />
        </svg>
        <span
          aria-hidden="true"
          className="yak-checkbox-indeterminate hidden h-0.5 w-2 rounded-full bg-current"
        />
      </BaseCheckbox.Indicator>
    </BaseCheckbox.Root>
  );
}
