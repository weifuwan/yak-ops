import {
  useLayoutEffect,
  useRef,
  useState,
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

interface IndicatorLayout {
  left: number;
  top: number;
  width: number;
  height: number;
}

export function Segmented<Value extends string = string>({
  className,
  isDisabled = false,
  onValueChange,
  options,
  value,
  ...props
}: SegmentedProps<Value>) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const buttonRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const [indicator, setIndicator] = useState<IndicatorLayout | null>(null);
  const [indicatorReady, setIndicatorReady] = useState(false);
  const selectedIndex = options.findIndex((option) => option.value === value);
  const firstEnabledIndex = options.findIndex((option) => !isDisabled && !option.isDisabled);
  const activeTabIndex =
    selectedIndex >= 0 && !isDisabled && !options[selectedIndex]?.isDisabled
      ? selectedIndex
      : firstEnabledIndex;

  useLayoutEffect(() => {
    const root = rootRef.current;
    const selectedButton = selectedIndex >= 0 ? buttonRefs.current[selectedIndex] : null;

    if (!root || !selectedButton) return;

    const updateIndicator = () => {
      setIndicator({
        left: selectedButton.offsetLeft,
        top: selectedButton.offsetTop,
        width: selectedButton.offsetWidth,
        height: selectedButton.offsetHeight,
      });
    };

    updateIndicator();

    const observer = new ResizeObserver(updateIndicator);
    observer.observe(root);
    observer.observe(selectedButton);

    return () => observer.disconnect();
  }, [selectedIndex, options.length]);

  useLayoutEffect(() => {
    if (!indicator || indicatorReady) return;

    const frame = requestAnimationFrame(() => setIndicatorReady(true));
    return () => cancelAnimationFrame(frame);
  }, [indicator, indicatorReady]);

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
      ref={rootRef}
      role="radiogroup"
      className={cn(
        "relative isolate inline-flex rounded-lg bg-[var(--yak-color-hover)] p-1",
        className,
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          "pointer-events-none absolute left-0 top-0 z-0 rounded-md bg-[var(--yak-components-panel-bg)] shadow-sm",
          "will-change-[transform,width,height] motion-reduce:transition-none",
          indicatorReady
            ? "transition-[transform,width,height,opacity] duration-[250ms] [transition-timing-function:cubic-bezier(0.22,1,0.36,1)]"
            : "transition-none",
        )}
        style={{
          width: indicator?.width ?? 0,
          height: indicator?.height ?? 0,
          opacity: selectedIndex >= 0 && indicator ? 1 : 0,
          transform: `translate3d(${indicator?.left ?? 0}px, ${indicator?.top ?? 0}px, 0)`,
        }}
      />

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
              "group relative z-10 cursor-pointer rounded-md bg-transparent px-3 py-1 text-xs font-medium outline-none",
              "transition-[color,opacity] duration-150 motion-reduce:transition-none",
              "focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-button-focus-ring)]",
              checked
                ? "text-[var(--yak-components-panel-text)]"
                : "text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-panel-text)]",
              disabled && "cursor-not-allowed opacity-40",
            )}
          >
            <span className="inline-block transition-transform duration-150 group-active:scale-[0.97] motion-reduce:transition-none">
              {option.label}
            </span>
          </button>
        );
      })}
    </div>
  );
}
