/*
 * Adapted from HeroUI v3 date components.
 * Modified for Yak Ops: React 18 compatibility and Yak UI design tokens.
 * Licensed under Apache-2.0; see yak-ops-ui/THIRD_PARTY_NOTICES.md.
 */

import type { ComponentPropsWithoutRef } from "react";
import {
  Calendar as CalendarPrimitive,
  CalendarCell as CalendarCellPrimitive,
  CalendarGrid as CalendarGridPrimitive,
  CalendarGridBody as CalendarGridBodyPrimitive,
  CalendarGridHeader as CalendarGridHeaderPrimitive,
  CalendarHeaderCell as CalendarHeaderCellPrimitive,
  CalendarHeading as CalendarHeadingPrimitive,
  type CalendarProps as CalendarPrimitiveProps,
  type DateValue,
} from "react-aria-components/Calendar";
import { Button as ButtonPrimitive } from "react-aria-components/Button";

import { cn } from "../cn";

function ChevronLeftIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4 fill-none stroke-current" strokeWidth="1.8">
      <path d="m12.5 4.5-5 5.5 5 5.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function ChevronRightIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4 fill-none stroke-current" strokeWidth="1.8">
      <path d="m7.5 4.5 5 5.5-5 5.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export type CalendarRootProps<T extends DateValue> = Omit<
  CalendarPrimitiveProps<T>,
  "className"
> & {
  className?: string;
};

export function CalendarRoot<T extends DateValue>({
  className,
  ...props
}: CalendarRootProps<T>) {
  return (
    <CalendarPrimitive
      {...props}
      className={cn(
        "w-[280px] rounded-xl bg-[var(--yak-components-panel-bg)] p-2 text-[var(--yak-components-panel-text)]",
        className,
      )}
    />
  );
}

export type CalendarHeaderProps = ComponentPropsWithoutRef<"div">;
export function CalendarHeader({ className, ...props }: CalendarHeaderProps) {
  return <div {...props} className={cn("mb-2 flex items-center gap-1", className)} />;
}

export type CalendarHeadingProps = Omit<
  ComponentPropsWithoutRef<typeof CalendarHeadingPrimitive>,
  "className"
> & { className?: string };

export function CalendarHeading({ className, ...props }: CalendarHeadingProps) {
  return (
    <CalendarHeadingPrimitive
      {...props}
      className={cn(
        "mx-1 flex-1 text-center text-[13px] font-semibold text-[var(--yak-components-panel-text)]",
        className,
      )}
    />
  );
}

export type CalendarNavButtonProps = Omit<
  ComponentPropsWithoutRef<typeof ButtonPrimitive>,
  "className"
> & {
  className?: string;
  slot?: "previous" | "next";
};

export function CalendarNavButton({
  children,
  className,
  slot,
  ...props
}: CalendarNavButtonProps) {
  return (
    <ButtonPrimitive
      {...props}
      slot={slot}
      className={cn(
        "inline-flex size-7 cursor-pointer items-center justify-center rounded-[var(--yak-radius-control-small)] text-[var(--yak-components-muted-text)] outline-none",
        "transition-[background-color,color,transform] duration-150 hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)] active:scale-[0.96] motion-reduce:transition-none",
        "focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-button-focus-ring)] disabled:cursor-not-allowed disabled:opacity-40",
        className,
      )}
    >
      {children ?? (slot === "previous" ? <ChevronLeftIcon /> : <ChevronRightIcon />)}
    </ButtonPrimitive>
  );
}

export type CalendarGridProps = Omit<
  ComponentPropsWithoutRef<typeof CalendarGridPrimitive>,
  "className"
> & { className?: string };

export function CalendarGrid({ className, ...props }: CalendarGridProps) {
  return (
    <CalendarGridPrimitive
      {...props}
      className={cn("w-full border-separate border-spacing-1", className)}
    />
  );
}

export type CalendarGridHeaderProps = Omit<
  ComponentPropsWithoutRef<typeof CalendarGridHeaderPrimitive>,
  "className"
> & { className?: string };

export function CalendarGridHeader({ className, ...props }: CalendarGridHeaderProps) {
  return <CalendarGridHeaderPrimitive {...props} className={className} />;
}

export type CalendarGridBodyProps = Omit<
  ComponentPropsWithoutRef<typeof CalendarGridBodyPrimitive>,
  "className"
> & { className?: string };

export function CalendarGridBody({ className, ...props }: CalendarGridBodyProps) {
  return <CalendarGridBodyPrimitive {...props} className={className} />;
}

export type CalendarHeaderCellProps = Omit<
  ComponentPropsWithoutRef<typeof CalendarHeaderCellPrimitive>,
  "className"
> & { className?: string };

export function CalendarHeaderCell({ className, ...props }: CalendarHeaderCellProps) {
  return (
    <CalendarHeaderCellPrimitive
      {...props}
      className={cn(
        "h-7 text-center text-[11px] font-medium text-[var(--yak-components-muted-text)]",
        className,
      )}
    />
  );
}

export type CalendarCellProps = Omit<
  ComponentPropsWithoutRef<typeof CalendarCellPrimitive>,
  "className"
> & {
  className?: string;
};

export function CalendarCell({
  children,
  className,
  ...props
}: CalendarCellProps) {
  return (
    <CalendarCellPrimitive
      {...props}
      className={cn("group size-8 p-0 text-center outline-none", className)}
    >
      {(values) => {
        const content =
          typeof children === "function"
            ? children(values)
            : children ?? values.formattedDate;
        return (
          <span
            className={cn(
              "mx-auto flex size-8 cursor-pointer items-center justify-center rounded-md text-[12px] tabular-nums",
              "transition-[background-color,color,transform,box-shadow] duration-150 group-hover:bg-[var(--yak-color-hover)] group-active:scale-[0.96] motion-reduce:transition-none",
              values.isSelected &&
                "bg-[var(--yak-color-primary)] text-white group-hover:bg-[var(--yak-color-primary-hover)]",
              values.isDisabled && "cursor-not-allowed opacity-35",
              values.isFocusVisible &&
                "ring-[3px] ring-[var(--yak-components-button-focus-ring)]",
            )}
          >
            {content}
          </span>
        );
      }}
    </CalendarCellPrimitive>
  );
}

export const Calendar = Object.assign(CalendarRoot, {
  Root: CalendarRoot,
  Header: CalendarHeader,
  Heading: CalendarHeading,
  NavButton: CalendarNavButton,
  Grid: CalendarGrid,
  GridHeader: CalendarGridHeader,
  GridBody: CalendarGridBody,
  HeaderCell: CalendarHeaderCell,
  Cell: CalendarCell,
});
