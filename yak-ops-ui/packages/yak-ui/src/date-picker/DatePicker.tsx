/*
 * Adapted from HeroUI v3 date components.
 * Modified for Yak Ops: React 18 compatibility and Yak UI design tokens.
 * Licensed under Apache-2.0; see yak-ops-ui/THIRD_PARTY_NOTICES.md.
 */

import {
  createContext,
  forwardRef,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ComponentPropsWithoutRef,
  type ReactNode,
} from "react";
import type { DateValue } from "react-aria-components/Calendar";
import { Button as ButtonPrimitive } from "react-aria-components/Button";
import {
  DatePicker as DatePickerPrimitive,
  Popover as PopoverPrimitive,
} from "react-aria-components/DatePicker";

import { cn } from "../cn";

type DatePickerContextValue = {
  triggerRef: { current: HTMLButtonElement | null };
};

const DatePickerContext = createContext<DatePickerContextValue>({
  triggerRef: { current: null },
});

function CalendarIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4 fill-none stroke-current" strokeWidth="1.6">
      <rect x="3" y="4.5" width="14" height="12.5" rx="2" />
      <path d="M6.5 2.8v3.4M13.5 2.8v3.4M3 8h14" strokeLinecap="round" />
    </svg>
  );
}

export type DatePickerRootProps<T extends DateValue> = Omit<
  ComponentPropsWithoutRef<typeof DatePickerPrimitive<T>>,
  "className"
> & {
  className?: string;
};

export function DatePickerRoot<T extends DateValue>({
  className,
  onOpenChange,
  ...props
}: DatePickerRootProps<T>) {
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const restoreFocusRef = useRef(false);

  useEffect(() => {
    if (!isOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (!event.metaKey && !event.ctrlKey && !event.altKey) restoreFocusRef.current = true;
    };
    window.addEventListener("keydown", onKeyDown, true);
    return () => window.removeEventListener("keydown", onKeyDown, true);
  }, [isOpen]);

  const handleOpenChange = (next: boolean) => {
    setIsOpen(next);
    if (!next && restoreFocusRef.current) {
      requestAnimationFrame(() => triggerRef.current?.focus());
    }
    restoreFocusRef.current = false;
    onOpenChange?.(next);
  };

  const contextValue = useMemo(() => ({ triggerRef }), []);

  return (
    <DatePickerContext.Provider value={contextValue}>
      <DatePickerPrimitive
        {...props}
        className={cn("grid min-w-0 gap-1.5", className)}
        onOpenChange={handleOpenChange}
      />
    </DatePickerContext.Provider>
  );
}

export type DatePickerTriggerProps = Omit<
  ComponentPropsWithoutRef<typeof ButtonPrimitive>,
  "className"
> & { className?: string };

export const DatePickerTrigger = forwardRef<HTMLButtonElement, DatePickerTriggerProps>(
  function DatePickerTrigger({ className, ...props }, forwardedRef) {
    const { triggerRef } = useContext(DatePickerContext);
    return (
      <ButtonPrimitive
        {...props}
        ref={(node) => {
          triggerRef.current = node;
          if (typeof forwardedRef === "function") forwardedRef(node);
          else if (forwardedRef) forwardedRef.current = node;
        }}
        className={cn(
          "inline-flex size-7 cursor-pointer items-center justify-center rounded-[var(--yak-radius-control-small)] text-[var(--yak-components-input-icon)] outline-none",
          "transition-[background-color,color,transform] duration-150 hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)] active:scale-[0.96] motion-reduce:transition-none",
          "focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-button-focus-ring)] disabled:cursor-not-allowed disabled:opacity-40",
          className,
        )}
      />
    );
  },
);

export type DatePickerTriggerIndicatorProps = ComponentPropsWithoutRef<"span"> & {
  children?: ReactNode;
};

export function DatePickerTriggerIndicator({
  children,
  className,
  ...props
}: DatePickerTriggerIndicatorProps) {
  return (
    <span
      {...props}
      aria-hidden="true"
      className={cn("inline-flex items-center justify-center", className)}
    >
      {children ?? <CalendarIcon />}
    </span>
  );
}

export type DatePickerPopoverProps = Omit<
  ComponentPropsWithoutRef<typeof PopoverPrimitive>,
  "children" | "className"
> & {
  children: ReactNode;
  className?: string;
};

export function DatePickerPopover({
  children,
  className,
  offset = 8,
  placement = "bottom",
  ...props
}: DatePickerPopoverProps) {
  return (
    <PopoverPrimitive
      {...props}
      offset={offset}
      placement={placement}
      className={cn(
        "z-60 min-w-[var(--trigger-width)] rounded-xl border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-1 text-[var(--yak-components-panel-text)] shadow-[var(--yak-components-panel-shadow)] outline-none",
        "transition-[opacity,transform] duration-150 data-entering:scale-[0.98] data-entering:opacity-0 data-exiting:scale-[0.98] data-exiting:opacity-0 motion-reduce:transition-none",
        className,
      )}
    >
      {children}
    </PopoverPrimitive>
  );
}

export const DatePicker = Object.assign(DatePickerRoot, {
  Root: DatePickerRoot,
  Trigger: DatePickerTrigger,
  TriggerIndicator: DatePickerTriggerIndicator,
  Popover: DatePickerPopover,
});
