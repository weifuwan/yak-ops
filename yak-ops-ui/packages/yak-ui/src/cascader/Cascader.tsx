import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Check, ChevronDown, ChevronRight, X } from "lucide-react";

import { cn } from "../cn";
import { Popover, PopoverContent, PopoverTrigger } from "../popover";
import { selectTriggerVariants } from "../select";

export type CascaderSize = "small" | "medium" | "large";
export type CascaderVariant = "filled" | "outlined";

export interface CascaderOption {
  label: string;
  value: string;
  children?: readonly CascaderOption[];
  disabled?: boolean;
  selectable?: boolean;
  icon?: ReactNode;
}

export interface CascaderProps {
  items: readonly CascaderOption[];
  value?: readonly string[] | null;
  defaultValue?: readonly string[] | null;
  onValueChange?: (value: string[] | null, options?: CascaderOption[]) => void;
  columnLabels?: readonly string[];
  size?: CascaderSize;
  variant?: CascaderVariant;
  placeholder?: string;
  emptyText?: ReactNode;
  disabled?: boolean;
  clearable?: boolean;
  className?: string;
  popupClassName?: string;
  ariaLabel?: string;
}

function findPathOptions(
  items: readonly CascaderOption[],
  path: readonly string[] | null | undefined,
): CascaderOption[] {
  if (!path?.length) return [];
  const result: CascaderOption[] = [];
  let currentItems = items;

  for (const value of path) {
    const option = currentItems.find((item) => item.value === value);
    if (!option) break;
    result.push(option);
    currentItems = option.children ?? [];
  }

  return result;
}

export function Cascader({
  ariaLabel,
  className,
  clearable = true,
  columnLabels = [],
  defaultValue = null,
  disabled = false,
  emptyText = "暂无可选项",
  items,
  onValueChange,
  placeholder = "请选择",
  popupClassName,
  size = "medium",
  value,
  variant = "outlined",
}: CascaderProps) {
  const [open, setOpen] = useState(false);
  const [innerValue, setInnerValue] = useState<string[] | null>(
    defaultValue ? [...defaultValue] : null,
  );
  const [activePath, setActivePath] = useState<string[]>([]);
  const selectedPath = value === undefined ? innerValue : value;
  const selectedKey = selectedPath?.join("\u001f") ?? "";

  const selectedOptions = useMemo(
    () => findPathOptions(items, selectedPath),
    [items, selectedKey],
  );
  const selectedLabel = selectedOptions.length === selectedPath?.length
    ? selectedOptions.map((option) => option.label).join(" / ")
    : "";

  const columns = useMemo(() => {
    const result: (readonly CascaderOption[])[] = [items];
    let currentItems = items;

    for (const value of activePath) {
      const activeOption = currentItems.find((option) => option.value === value);
      if (!activeOption) break;
      currentItems = activeOption.children ?? [];
      result.push(currentItems);
      if (currentItems.length === 0) break;
    }

    return result;
  }, [activePath, items]);

  useEffect(() => {
    if (!open) return;
    if (selectedPath?.length) {
      setActivePath([...selectedPath.slice(0, -1)]);
      return;
    }
    const first = items[0];
    setActivePath(first ? [first.value] : []);
  }, [open, items, selectedKey]);

  const selectOption = (path: string[]) => {
    const options = findPathOptions(items, path);
    if (options.length !== path.length) return;
    if (value === undefined) setInnerValue(path);
    onValueChange?.(path, options);
    setOpen(false);
  };

  const clearValue = () => {
    if (disabled) return;
    if (value === undefined) setInnerValue(null);
    onValueChange?.(null);
  };

  const handleOptionClick = (option: CascaderOption, columnIndex: number) => {
    if (option.disabled) return;
    const nextPath = [...activePath.slice(0, columnIndex), option.value];
    const children = option.children ?? [];

    if (children.length > 0) {
      setActivePath(nextPath);
      return;
    }

    if (option.selectable === false) return;
    selectOption(nextPath);
  };

  const hasValue = Boolean(selectedLabel);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <div className={cn("relative w-full", className)}>
        <PopoverTrigger
          type="button"
          disabled={disabled}
          aria-label={ariaLabel ?? placeholder}
          data-placeholder={hasValue ? undefined : ""}
          className={cn(selectTriggerVariants({ size, variant }), "w-full")}
        >
          <span className={cn(
            "min-w-0 flex-1 truncate text-left",
            clearable && hasValue && !disabled && "pr-6",
          )} title={selectedLabel || undefined}>
            {selectedLabel || placeholder}
          </span>
          <ChevronDown
            aria-hidden="true"
            size={15}
            className="shrink-0 text-[var(--yak-components-input-icon)] transition-transform duration-150 group-data-popup-open/select-trigger:rotate-180"
          />
        </PopoverTrigger>

        {clearable && hasValue && !disabled ? (
          <button
            type="button"
            aria-label="清空选择"
            title="清空选择"
            className="absolute right-7 top-1/2 z-[1] flex size-5 -translate-y-1/2 cursor-pointer items-center justify-center rounded text-[var(--yak-components-input-icon)] outline-none transition-colors hover:bg-[var(--yak-components-input-bg-hover)] hover:text-[var(--yak-components-input-text)] focus-visible:ring-2 focus-visible:ring-[var(--yak-components-button-focus-ring)]"
            onClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
              clearValue();
            }}
          >
            <X size={13} />
          </button>
        ) : null}
      </div>

      <PopoverContent
        align="start"
        sideOffset={4}
        className={cn(
          "w-[min(760px,calc(100vw-32px))] overflow-hidden !rounded-[var(--yak-radius-control-medium)] !border-[var(--yak-components-select-border)] !bg-[var(--yak-components-select-bg)] !p-0 !shadow-[var(--yak-components-select-shadow)]",
          popupClassName,
        )}
      >
        <div className="flex max-h-80 min-h-36 divide-x divide-[var(--yak-components-select-border)]">
          {columns.map((column, columnIndex) => (
            <div key={columnIndex} className="min-w-0 flex-1 overflow-y-auto p-1.5">
              {columnLabels[columnIndex] ? (
                <div className="px-2 pb-1.5 pt-1 text-[11px] font-medium text-[var(--yak-components-muted-text)]">
                  {columnLabels[columnIndex]}
                </div>
              ) : null}
              {column.length === 0 ? (
                <div className="px-2 py-6 text-center text-xs text-[var(--yak-components-muted-text)]">
                  {emptyText}
                </div>
              ) : (
                <div className="space-y-0.5">
                  {column.map((option) => {
                    const children = option.children ?? [];
                    const hasChildren = children.length > 0;
                    const active = activePath[columnIndex] === option.value;
                    const selected = selectedPath?.[columnIndex] === option.value;

                    return (
                      <button
                        key={option.value}
                        type="button"
                        disabled={option.disabled}
                        aria-current={selected ? "true" : undefined}
                        className={cn(
                          "flex min-h-7 w-full items-center gap-2 rounded-[var(--yak-radius-control-small)] px-2 py-1 text-left text-xs leading-5 text-[var(--yak-components-select-item-text)] outline-none transition-colors",
                          active && "bg-[var(--yak-components-select-item-bg-hover)]",
                          selected && "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]",
                          option.disabled && "cursor-not-allowed opacity-45",
                          !option.disabled && "cursor-pointer hover:bg-[var(--yak-components-select-item-bg-hover)]",
                        )}
                        onMouseEnter={() => {
                          if (!option.disabled && hasChildren) {
                            setActivePath([...activePath.slice(0, columnIndex), option.value]);
                          }
                        }}
                        onClick={() => handleOptionClick(option, columnIndex)}
                      >
                        {option.icon ? (
                          <span className="flex size-4 shrink-0 items-center justify-center">{option.icon}</span>
                        ) : null}
                        <span className="min-w-0 flex-1 truncate">{option.label}</span>
                        {hasChildren ? (
                          <ChevronRight size={13} className="shrink-0 text-[var(--yak-components-input-icon)]" />
                        ) : selected ? (
                          <Check size={14} className="shrink-0 text-[var(--yak-components-select-indicator)]" />
                        ) : null}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          ))}
        </div>
      </PopoverContent>
    </Popover>
  );
}
