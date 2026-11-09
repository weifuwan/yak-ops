import {
  useRef,
  type ComponentPropsWithoutRef,
  type KeyboardEvent,
  type ReactNode,
} from "react";

import { cn } from "../cn";

export interface SegmentedOption<Value extends string = string> {
  value: Value;
  label: ReactNode;
  isDisabled?: boolean;
}

export type SegmentedProps<Value extends string = string> = Omit<
  ComponentPropsWithoutRef<"div">,
  "children" | "onChange"
> & {
  value?: Value;
  options: readonly SegmentedOption<Value>[];
  onValueChange: (value: Value) => void;
  isDisabled?: boolean;
};

export function Segmented<Value extends string = string>({
  className,
  isDisabled = false,
  onValueChange,
  options,
  value,
  ...props
}: SegmentedProps<Value>) {
  const buttonRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const selectedIndex = options.findIndex((option) => option.value === value);
  const firstEnabledIndex = options.findIndex((option) => !isDisabled && !option.isDisabled);
  const activeTabIndex =
    selectedIndex >= 0 && !isDisabled && !options[selectedIndex]?.isDisabled
      ? selectedIndex
      : firstEnabledIndex;

  const selectIndex = (index: number) => {
    const option = options[index];
    if (!option || isDisabled || option.isDisabled) return;
    onValueChange(option.value);
    buttonRefs.current[index]?.focus();
  };

  const move = (currentIndex: number, step: 1 | -1) => {
    for (let offset = 1; offset <= options.length; offset += 1) {
      const nextIndex = (currentIndex + step * offset + options.length) % options.length;
      const option = options[nextIndex];
      if (option && !isDisabled && !option.isDisabled) {
        selectIndex(nextIndex);
        return;
      }
    }
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    switch (event.key) {
      case "ArrowRight":
      case "ArrowDown":
        event.preventDefault();
        move(index, 1);
        break;
      case "ArrowLeft":
      case "ArrowUp":
        event.preventDefault();
        move(index, -1);
        break;
      case "Home":
        event.preventDefault();
        if (firstEnabledIndex >= 0) selectIndex(firstEnabledIndex);
        break;
      case "End": {
        event.preventDefault();
        for (let nextIndex = options.length - 1; nextIndex >= 0; nextIndex -= 1) {
          const option = options[nextIndex];
          if (option && !isDisabled && !option.isDisabled) {
            selectIndex(nextIndex);
            break;
          }
        }
        break;
      }
    }
  };

  return (
    <div
      {...props}
      role="radiogroup"
      className={cn(
        "inline-flex rounded-lg bg-[var(--yak-color-hover)] p-1",
        className,
      )}
    >
      {options.map((option, index) => {
        const checked = option.value === value;
        const disabled = isDisabled || option.isDisabled;
        return (
          <button
            key={option.value}
            ref={(node) => {
              buttonRefs.current[index] = node;
            }}
            type="button"
            role="radio"
            aria-checked={checked}
            disabled={disabled}
            tabIndex={index === activeTabIndex ? 0 : -1}
            onClick={() => {
              if (!checked) onValueChange(option.value);
            }}
            onKeyDown={(event) => handleKeyDown(event, index)}
            className={cn(
              "rounded-md px-3 py-1 text-xs font-medium outline-none transition-[background-color,color,box-shadow] duration-150",
              "focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-button-focus-ring)]",
              checked
                ? "bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)] shadow-sm"
                : "text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-panel-text)]",
              disabled && "cursor-not-allowed opacity-40",
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
