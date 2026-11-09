/*
 * Adapted from HeroUI v3 date components.
 * Modified for Yak Ops: React 18 compatibility and Yak UI design tokens.
 * Licensed under Apache-2.0; see yak-ops-ui/THIRD_PARTY_NOTICES.md.
 */

import type { ComponentPropsWithoutRef } from "react";
import {
  CalendarCell as CalendarCellPrimitive,
  RangeCalendar as RangeCalendarPrimitive,
  type DateValue,
  type RangeCalendarProps as RangeCalendarPrimitiveProps,
} from "react-aria-components/RangeCalendar";

import {
  CalendarGrid,
  CalendarGridBody,
  CalendarGridHeader,
  CalendarHeader,
  CalendarHeaderCell,
  CalendarHeading,
  CalendarNavButton,
} from "../calendar/Calendar";
import { cn } from "../cn";

import "./range-calendar.css";

export type RangeCalendarRootProps<T extends DateValue> = Omit<
  RangeCalendarPrimitiveProps<T>,
  "className"
> & {
  className?: string;
};

export function RangeCalendarRoot<T extends DateValue>({
  className,
  ...props
}: RangeCalendarRootProps<T>) {
  return (
    <RangeCalendarPrimitive
      {...props}
      className={cn("yak-range-calendar", className)}
    />
  );
}

export type RangeCalendarCellProps = Omit<
  ComponentPropsWithoutRef<typeof CalendarCellPrimitive>,
  "className"
> & {
  className?: string;
};

export function RangeCalendarCell({
  children,
  className,
  ...props
}: RangeCalendarCellProps) {
  return (
    <CalendarCellPrimitive
      {...props}
      className={cn("yak-range-calendar__cell", className)}
    >
      {(values) => {
        const content =
          typeof children === "function"
            ? children(values)
            : children ?? values.formattedDate;

        return <span className="yak-range-calendar__cell-button">{content}</span>;
      }}
    </CalendarCellPrimitive>
  );
}

export const RangeCalendar = Object.assign(RangeCalendarRoot, {
  Root: RangeCalendarRoot,
  Header: CalendarHeader,
  Heading: CalendarHeading,
  NavButton: CalendarNavButton,
  Grid: CalendarGrid,
  GridHeader: CalendarGridHeader,
  GridBody: CalendarGridBody,
  HeaderCell: CalendarHeaderCell,
  Cell: RangeCalendarCell,
});
