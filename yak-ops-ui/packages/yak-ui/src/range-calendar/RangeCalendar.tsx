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
      className={cn(
        "w-[280px] rounded-xl bg-[var(--yak-components-panel-bg)] p-2 text-[var(--yak-components-panel-text)]",
        className,
      )}
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
      className={cn("group size-8 p-0 text-center outline-none", className)}
    >
      {(values) => {
        const content =
          typeof children === "function"
            ? children(values)
            : children ?? values.formattedDate;
        const cap = values.isSelected && (values.isSelectionStart || values.isSelectionEnd);
        return (
          <span
            className={cn(
              "mx-auto flex size-8 cursor-pointer items-center justify-center rounded-md text-[12px] tabular-nums",
              "transition-[background-color,color,transform,box-shadow] duration-150 group-hover:bg-[var(--yak-color-hover)] group-active:scale-[0.96] motion-reduce:transition-none",
              values.isSelected &&
                "rounded-none bg-[var(--yak-color-primary-subtle)] text-[var(--yak-components-panel-text)]",
              values.isSelectionStart && "rounded-l-md",
              values.isSelectionEnd && "rounded-r-md",
              cap &&
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
