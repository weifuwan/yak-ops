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

import "./calendar.css";

function ChevronLeftIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 20 20"
      className="size-4 fill-none stroke-current"
      strokeWidth="1.8"
    >
      <path d="m12.5 4.5-5 5.5 5 5.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function ChevronRightIcon({ className }: { className?: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 20 20"
      className={cn("size-4 fill-none stroke-current", className)}
      strokeWidth="1.8"
    >
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
  return <CalendarPrimitive {...props} className={cn("yak-calendar", className)} />;
}

export type CalendarHeaderProps = ComponentPropsWithoutRef<"div">;

export function CalendarHeader({ className, ...props }: CalendarHeaderProps) {
  return <div {...props} className={cn("yak-calendar__header", className)} />;
}

export type CalendarHeadingProps = Omit<
  ComponentPropsWithoutRef<typeof CalendarHeadingPrimitive>,
  "className"
> & {
  className?: string;
};

export function CalendarHeading({ className, ...props }: CalendarHeadingProps) {
  return (
    <div className="yak-calendar__heading-group">
      <CalendarHeadingPrimitive
        {...props}
        className={cn("yak-calendar__heading", className)}
      />
      <ChevronRightIcon className="yak-calendar__heading-indicator" />
    </div>
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
      data-nav={slot}
      className={cn("yak-calendar__nav-button", className)}
    >
      {children ?? (slot === "previous" ? <ChevronLeftIcon /> : <ChevronRightIcon />)}
    </ButtonPrimitive>
  );
}

export type CalendarGridProps = Omit<
  ComponentPropsWithoutRef<typeof CalendarGridPrimitive>,
  "className"
> & {
  className?: string;
};

export function CalendarGrid({
  className,
  weekdayStyle = "short",
  ...props
}: CalendarGridProps) {
  return (
    <CalendarGridPrimitive
      {...props}
      weekdayStyle={weekdayStyle}
      className={cn("yak-calendar__grid", className)}
    />
  );
}

export type CalendarGridHeaderProps = Omit<
  ComponentPropsWithoutRef<typeof CalendarGridHeaderPrimitive>,
  "className"
> & {
  className?: string;
};

export function CalendarGridHeader({ className, ...props }: CalendarGridHeaderProps) {
  return (
    <CalendarGridHeaderPrimitive
      {...props}
      className={cn("yak-calendar__grid-header", className)}
    />
  );
}

export type CalendarGridBodyProps = Omit<
  ComponentPropsWithoutRef<typeof CalendarGridBodyPrimitive>,
  "className"
> & {
  className?: string;
};

export function CalendarGridBody({ className, ...props }: CalendarGridBodyProps) {
  return (
    <CalendarGridBodyPrimitive
      {...props}
      className={cn("yak-calendar__grid-body", className)}
    />
  );
}

export type CalendarHeaderCellProps = Omit<
  ComponentPropsWithoutRef<typeof CalendarHeaderCellPrimitive>,
  "className"
> & {
  className?: string;
};

export function CalendarHeaderCell({ className, ...props }: CalendarHeaderCellProps) {
  return (
    <CalendarHeaderCellPrimitive
      {...props}
      className={cn("yak-calendar__header-cell", className)}
    />
  );
}

export type CalendarCellProps = Omit<
  ComponentPropsWithoutRef<typeof CalendarCellPrimitive>,
  "className"
> & {
  className?: string;
};

export function CalendarCell({ className, ...props }: CalendarCellProps) {
  return (
    <CalendarCellPrimitive
      {...props}
      className={cn("yak-calendar__cell", className)}
    />
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
