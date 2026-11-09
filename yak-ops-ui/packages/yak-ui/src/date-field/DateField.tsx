/*
 * Adapted from HeroUI v3 date components.
 * Modified for Yak Ops: React 18 compatibility and Yak UI design tokens.
 * Licensed under Apache-2.0; see yak-ops-ui/THIRD_PARTY_NOTICES.md.
 */

import type { ComponentPropsWithoutRef, ReactNode } from "react";
import type { DateValue } from "react-aria-components/Calendar";
import {
  DateField as DateFieldPrimitive,
  DateInput as DateInputPrimitive,
  DateSegment as DateSegmentPrimitive,
  type DateInputProps as DateInputPrimitiveProps,
  type DateSegmentProps as DateSegmentPrimitiveProps,
} from "react-aria-components/DateField";
import { Group as GroupPrimitive } from "react-aria-components/Group";

import { cn } from "../cn";

export type DateFieldRootProps<T extends DateValue> = Omit<
  ComponentPropsWithoutRef<typeof DateFieldPrimitive<T>>,
  "className"
> & {
  className?: string;
  fullWidth?: boolean;
};

export function DateFieldRoot<T extends DateValue>({
  className,
  fullWidth = false,
  ...props
}: DateFieldRootProps<T>) {
  return (
    <DateFieldPrimitive
      {...props}
      className={cn("grid min-w-0 gap-1.5", fullWidth && "w-full", className)}
    />
  );
}

export type DateInputGroupProps = Omit<
  ComponentPropsWithoutRef<typeof GroupPrimitive>,
  "className"
> & {
  className?: string;
  fullWidth?: boolean;
  variant?: "filled" | "outlined";
};

export function DateInputGroup({
  className,
  fullWidth = false,
  variant = "filled",
  ...props
}: DateInputGroupProps) {
  return (
    <GroupPrimitive
      {...props}
      className={cn(
        "flex h-8 min-w-0 items-center rounded-[var(--yak-radius-control-medium)] border text-[12px] text-[var(--yak-components-input-text)] outline-none",
        "transition-[background-color,border-color,box-shadow] duration-150 motion-reduce:transition-none",
        "focus-within:border-[var(--yak-components-input-border-focus)] focus-within:ring-[3px] focus-within:ring-[var(--yak-components-input-focus-ring)]",
        "data-disabled:cursor-not-allowed data-disabled:bg-[var(--yak-components-input-bg-disabled)] data-disabled:text-[var(--yak-components-input-text-disabled)]",
        "data-invalid:border-[var(--yak-components-input-border-danger)] data-invalid:ring-[3px] data-invalid:ring-[var(--yak-components-input-danger-ring)]",
        variant === "filled"
          ? "border-transparent bg-[var(--yak-components-input-bg)] hover:bg-[var(--yak-components-input-bg-hover)]"
          : "border-[var(--yak-components-input-border)] bg-[var(--yak-components-input-bg-focus)] hover:border-[var(--yak-components-input-border-focus)]",
        fullWidth && "w-full",
        className,
      )}
    />
  );
}

export type DateInputGroupInputProps = Omit<DateInputPrimitiveProps, "className"> & {
  className?: string;
};

export function DateInputGroupInput({ className, ...props }: DateInputGroupInputProps) {
  return (
    <DateInputPrimitive
      {...props}
      className={cn(
        "flex min-w-0 flex-1 items-center px-2.5 tabular-nums outline-none",
        className,
      )}
    />
  );
}

export type DateInputGroupSegmentProps = Omit<DateSegmentPrimitiveProps, "className"> & {
  className?: string;
};

export function DateInputGroupSegment({
  className,
  segment,
  ...props
}: DateInputGroupSegmentProps) {
  return (
    <DateSegmentPrimitive
      {...props}
      segment={segment}
      className={cn(
        "rounded-[3px] px-0.5 text-center outline-none",
        "data-placeholder:text-[var(--yak-components-input-placeholder)]",
        "data-focused:bg-[var(--yak-color-primary-subtle)] data-focused:text-[var(--yak-color-primary)]",
        "data-disabled:text-[var(--yak-components-input-text-disabled)]",
        className,
      )}
    />
  );
}

type DateInputAdornmentProps = ComponentPropsWithoutRef<"div"> & {
  children?: ReactNode;
};

export function DateInputGroupInputContainer({
  className,
  ...props
}: DateInputAdornmentProps) {
  return <div {...props} className={cn("flex min-w-0 flex-1 items-center", className)} />;
}

export function DateInputGroupPrefix({ className, ...props }: DateInputAdornmentProps) {
  return (
    <div
      {...props}
      className={cn(
        "flex shrink-0 items-center pl-2.5 text-[var(--yak-components-input-icon)]",
        className,
      )}
    />
  );
}

export function DateInputGroupSuffix({ className, ...props }: DateInputAdornmentProps) {
  return (
    <div
      {...props}
      className={cn(
        "flex shrink-0 items-center pr-1 text-[var(--yak-components-input-icon)]",
        className,
      )}
    />
  );
}

export const DateField = Object.assign(DateFieldRoot, {
  Root: DateFieldRoot,
  Group: DateInputGroup,
  Input: DateInputGroupInput,
  InputContainer: DateInputGroupInputContainer,
  Segment: DateInputGroupSegment,
  Prefix: DateInputGroupPrefix,
  Suffix: DateInputGroupSuffix,
});
