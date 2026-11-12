import {
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from "react";

import { cn } from "../cn";
import { Input } from "../input";
import { Popover, PopoverContent, PopoverTrigger } from "../popover";
import { selectTriggerVariants } from "../select";

export type TreeSelectSize = "small" | "medium" | "large";
export type TreeSelectVariant = "filled" | "outlined";

export interface TreeSelectOption {
  label: string;
  value: string;
  children?: readonly TreeSelectOption[];
  disabled?: boolean;
  selectable?: boolean;
  searchText?: string;
  icon?: ReactNode;
}

export interface TreeSelectProps {
  items: readonly TreeSelectOption[];
  value?: string | null;
  defaultValue?: string | null;
  onValueChange?: (value: string | null, option?: TreeSelectOption) => void;
  expandedValues?: readonly string[];
  defaultExpandedValues?: readonly string[];
  onExpandedValuesChange?: (values: string[]) => void;
  size?: TreeSelectSize;
  variant?: TreeSelectVariant;
  placeholder?: string;
  searchPlaceholder?: string;
  emptyText?: ReactNode;
  disabled?: boolean;
  clearable?: boolean;
  showSearch?: boolean;
  className?: string;
  popupClassName?: string;
  ariaLabel?: string;
}

interface VisibleTreeNode {
  option: TreeSelectOption;
  level: number;
  parentValue?: string;
  expandable: boolean;
  expanded: boolean;
}

function findOption(
  items: readonly TreeSelectOption[],
  value: string | null | undefined,
): TreeSelectOption | undefined {
  if (!value) return undefined;

  for (const item of items) {
    if (item.value === value) return item;
    const child = findOption(item.children ?? [], value);
    if (child) return child;
  }

  return undefined;
}

function filterOptions(
  items: readonly TreeSelectOption[],
  query: string,
): TreeSelectOption[] {
  const normalizedQuery = query.trim().toLocaleLowerCase();
  if (!normalizedQuery) return [...items];

  const visit = (option: TreeSelectOption): TreeSelectOption | null => {
    const children = option.children ?? [];
    const selfMatches = [option.label, option.value, option.searchText]
      .filter(Boolean)
      .join(" ")
      .toLocaleLowerCase()
      .includes(normalizedQuery);

    if (selfMatches) return { ...option, children };

    const matchedChildren = children
      .map(visit)
      .filter((child): child is TreeSelectOption => child !== null);

    if (matchedChildren.length === 0) return null;
    return { ...option, children: matchedChildren };
  };

  return items
    .map(visit)
    .filter((option): option is TreeSelectOption => option !== null);
}

function collectExpandableValues(
  items: readonly TreeSelectOption[],
  result = new Set<string>(),
): Set<string> {
  items.forEach((item) => {
    if ((item.children?.length ?? 0) > 0) {
      result.add(item.value);
      collectExpandableValues(item.children ?? [], result);
    }
  });
  return result;
}

function flattenVisibleOptions(
  items: readonly TreeSelectOption[],
  expandedValues: ReadonlySet<string>,
  level = 1,
  parentValue?: string,
): VisibleTreeNode[] {
  const rows: VisibleTreeNode[] = [];

  items.forEach((option) => {
    const children = option.children ?? [];
    const expandable = children.length > 0;
    const expanded = expandable && expandedValues.has(option.value);

    rows.push({
      option,
      level,
      parentValue,
      expandable,
      expanded,
    });

    if (expanded) {
      rows.push(
        ...flattenVisibleOptions(
          children,
          expandedValues,
          level + 1,
          option.value,
        ),
      );
    }
  });

  return rows;
}

function SearchIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4" fill="none">
      <circle cx="8.5" cy="8.5" r="4.5" stroke="currentColor" strokeWidth="1.5" />
      <path
        d="m12 12 3.5 3.5"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="1.5"
      />
    </svg>
  );
}

function ChevronDownIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4" fill="none">
      <path
        d="m6 8 4 4 4-4"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="1.5"
      />
    </svg>
  );
}

function ChevronRightIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="size-3.5" fill="none">
      <path
        d="m8 6 4 4-4 4"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="1.5"
      />
    </svg>
  );
}

function ClearIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="size-3.5" fill="none">
      <path
        d="m6.5 6.5 7 7m0-7-7 7"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="1.5"
      />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4" fill="none">
      <path
        d="m5.5 10 3 3 6-6"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="1.6"
      />
    </svg>
  );
}

export function TreeSelect({
  ariaLabel,
  className,
  clearable = true,
  defaultExpandedValues = [],
  defaultValue = null,
  disabled = false,
  emptyText = "暂无匹配项",
  expandedValues,
  items,
  onExpandedValuesChange,
  onValueChange,
  placeholder = "请选择",
  popupClassName,
  searchPlaceholder = "搜索",
  showSearch = true,
  size = "medium",
  value,
  variant = "outlined",
}: TreeSelectProps) {
  const treeId = useId().replace(/:/g, "");
  const treeRef = useRef<HTMLDivElement | null>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [innerValue, setInnerValue] = useState<string | null>(defaultValue);
  const [innerExpandedValues, setInnerExpandedValues] = useState<string[]>(
    () => [...defaultExpandedValues],
  );
  const [activeValue, setActiveValue] = useState<string | null>(null);

  const selectedValue = value === undefined ? innerValue : value;
  const selectedOption = useMemo(
    () => findOption(items, selectedValue),
    [items, selectedValue],
  );

  const controlledExpanded = expandedValues !== undefined;
  const currentExpandedValues = controlledExpanded
    ? expandedValues
    : innerExpandedValues;
  const currentExpandedSet = useMemo(
    () => new Set(currentExpandedValues),
    [currentExpandedValues],
  );

  const filteredItems = useMemo(
    () => filterOptions(items, query),
    [items, query],
  );
  const searchExpandedValues = useMemo(
    () => collectExpandableValues(filteredItems),
    [filteredItems],
  );
  const effectiveExpandedSet = query.trim()
    ? searchExpandedValues
    : currentExpandedSet;
  const visibleRows = useMemo(
    () => flattenVisibleOptions(filteredItems, effectiveExpandedSet),
    [effectiveExpandedSet, filteredItems],
  );

  const activeIndex = visibleRows.findIndex(
    (row) => row.option.value === activeValue,
  );

  useEffect(() => {
    if (!open) return;
    const selectedVisible = visibleRows.some(
      (row) => row.option.value === selectedValue,
    );
    setActiveValue(
      selectedVisible ? selectedValue ?? null : visibleRows[0]?.option.value ?? null,
    );
  }, [open, selectedValue, visibleRows]);

  useEffect(() => {
    if (!open || !activeValue) return;
    const activeNode = treeRef.current?.querySelector<HTMLElement>(
      `[data-tree-select-value="${encodeURIComponent(activeValue)}"]`,
    );
    activeNode?.scrollIntoView({ block: "nearest", inline: "nearest" });
  }, [activeValue, open]);

  const setExpanded = (next: Set<string>) => {
    const values = Array.from(next);
    if (!controlledExpanded) setInnerExpandedValues(values);
    onExpandedValuesChange?.(values);
  };

  const toggleExpanded = (optionValue: string) => {
    const next = new Set(currentExpandedSet);
    if (next.has(optionValue)) next.delete(optionValue);
    else next.add(optionValue);
    setExpanded(next);
  };

  const selectOption = (option: TreeSelectOption) => {
    if (option.disabled || option.selectable === false) return;
    if (value === undefined) setInnerValue(option.value);
    onValueChange?.(option.value, option);
    setOpen(false);
    setQuery("");
  };

  const clearValue = () => {
    if (disabled) return;
    if (value === undefined) setInnerValue(null);
    onValueChange?.(null);
    setQuery("");
  };

  const setActiveIndex = (index: number) => {
    if (visibleRows.length === 0) return;
    const nextIndex = Math.max(0, Math.min(visibleRows.length - 1, index));
    setActiveValue(visibleRows[nextIndex]?.option.value ?? null);
  };

  const handleTreeKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (visibleRows.length === 0) {
      if (event.key === "Escape") setOpen(false);
      return;
    }

    const index = activeIndex >= 0 ? activeIndex : 0;
    const row = visibleRows[index];
    if (!row) return;

    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        setActiveIndex(index + 1);
        break;
      case "ArrowUp":
        event.preventDefault();
        setActiveIndex(index - 1);
        break;
      case "Home":
        event.preventDefault();
        setActiveIndex(0);
        break;
      case "End":
        event.preventDefault();
        setActiveIndex(visibleRows.length - 1);
        break;
      case "ArrowRight":
        if (!query.trim() && row.expandable && !row.expanded) {
          event.preventDefault();
          toggleExpanded(row.option.value);
        } else if (
          row.expandable &&
          row.expanded &&
          visibleRows[index + 1]?.parentValue === row.option.value
        ) {
          event.preventDefault();
          setActiveIndex(index + 1);
        }
        break;
      case "ArrowLeft":
        if (!query.trim() && row.expandable && row.expanded) {
          event.preventDefault();
          toggleExpanded(row.option.value);
        } else if (row.parentValue) {
          event.preventDefault();
          const parentIndex = visibleRows.findIndex(
            (item) => item.option.value === row.parentValue,
          );
          if (parentIndex >= 0) setActiveIndex(parentIndex);
        }
        break;
      case "Enter":
      case " ":
        event.preventDefault();
        if (!row.option.disabled && row.option.selectable !== false) {
          selectOption(row.option);
        } else if (!query.trim() && row.expandable) {
          toggleExpanded(row.option.value);
        }
        break;
      case "Escape":
        event.preventDefault();
        setOpen(false);
        break;
      default:
        break;
    }
  };

  const hasValue = Boolean(selectedValue);

  return (
    <Popover
      open={open}
      onOpenChange={(nextOpen) => {
        setOpen(nextOpen);
        if (!nextOpen) setQuery("");
      }}
    >
      <div className={cn("relative w-full", className)}>
        <PopoverTrigger
          type="button"
          disabled={disabled}
          aria-label={ariaLabel ?? placeholder}
          data-placeholder={hasValue ? undefined : ""}
          className={selectTriggerVariants({ size, variant })}
        >
          <span
            className={cn(
              "min-w-0 flex-1 truncate",
              clearable && hasValue && !disabled && "pr-6",
            )}
            title={selectedOption?.label ?? selectedValue ?? undefined}
          >
            {selectedOption?.label ?? selectedValue ?? placeholder}
          </span>
          <span
            aria-hidden="true"
            className="shrink-0 text-[var(--yak-components-input-icon)] transition-[rotate] duration-150 ease group-data-popup-open/select-trigger:rotate-180 motion-reduce:transition-none"
          >
            <ChevronDownIcon />
          </span>
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
            <ClearIcon />
          </button>
        ) : null}
      </div>

      <PopoverContent
        align="start"
        sideOffset={4}
        className={cn(
          "w-[var(--anchor-width)] min-w-64 max-w-md overflow-hidden !rounded-[var(--yak-radius-control-medium)] !border-[var(--yak-components-select-border)] !bg-[var(--yak-components-select-bg)] !p-0 !shadow-[var(--yak-components-select-shadow)]",
          popupClassName,
        )}
      >
        {showSearch ? (
          <div className="border-b border-[var(--yak-components-select-border)] p-2">
            <div className="relative">
              <span className="pointer-events-none absolute inset-y-0 left-2.5 flex items-center text-[var(--yak-components-input-icon)]">
                <SearchIcon />
              </span>
              <Input
                autoFocus
                size={size}
                variant="outlined"
                value={query}
                placeholder={searchPlaceholder}
                aria-label={searchPlaceholder}
                className="!pl-8"
                onChange={(event) => setQuery(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "ArrowDown" && visibleRows.length > 0) {
                    event.preventDefault();
                    setActiveValue(visibleRows[0]?.option.value ?? null);
                    treeRef.current?.focus();
                    return;
                  }
                  if (event.key !== "Escape" && event.key !== "Tab") {
                    event.stopPropagation();
                  }
                }}
              />
            </div>
          </div>
        ) : null}

        {visibleRows.length === 0 ? (
          <div className="px-3 py-6 text-center text-[length:var(--yak-font-size-control-small)] text-[var(--yak-components-muted-text)]">
            {emptyText}
          </div>
        ) : (
          <div
            ref={treeRef}
            id={treeId}
            role="tree"
            tabIndex={0}
            aria-label={ariaLabel ?? placeholder}
            aria-activedescendant={
              activeValue ? treeId + "-node-" + encodeURIComponent(activeValue) : undefined
            }
            className="max-h-80 overflow-y-auto p-1 outline-none"
            onKeyDown={handleTreeKeyDown}
          >
            {visibleRows.map((row) => {
              const option = row.option;
              const selected = option.value === selectedValue;
              const active = option.value === activeValue;
              const selectable = option.selectable !== false;
              const disabledOption = Boolean(option.disabled);
              const rowId =
                treeId + "-node-" + encodeURIComponent(option.value);

              return (
                <div
                  key={option.value}
                  id={rowId}
                  data-tree-select-value={encodeURIComponent(option.value)}
                  role="treeitem"
                  aria-level={row.level}
                  aria-expanded={row.expandable ? row.expanded : undefined}
                  aria-selected={selectable ? selected : undefined}
                  aria-disabled={disabledOption || undefined}
                  className={cn(
                    "flex min-h-8 items-center rounded-[var(--yak-radius-control-small)] pr-1 text-[var(--yak-components-select-item-text)]",
                    active && !selected && "bg-[var(--yak-components-select-item-bg-hover)]",
                    selected &&
                      "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]",
                    disabledOption && "opacity-45",
                  )}
                  style={{ paddingLeft: 4 + (row.level - 1) * 16 }}
                  onMouseMove={() => setActiveValue(option.value)}
                >
                  <button
                    type="button"
                    tabIndex={-1}
                    aria-label={
                      row.expandable
                        ? row.expanded
                          ? "收起 " + option.label
                          : "展开 " + option.label
                        : undefined
                    }
                    disabled={!row.expandable}
                    className={cn(
                      "flex size-6 shrink-0 items-center justify-center rounded outline-none",
                      row.expandable
                        ? "cursor-pointer text-[var(--yak-components-input-icon)] hover:bg-[var(--yak-components-input-bg-hover)]"
                        : "cursor-default text-transparent",
                    )}
                    onClick={(event) => {
                      event.stopPropagation();
                      if (!query.trim() && row.expandable) {
                        toggleExpanded(option.value);
                      }
                    }}
                  >
                    {row.expandable ? (
                      <span
                        className={cn(
                          "transition-transform duration-150 motion-reduce:transition-none",
                          row.expanded && "rotate-90",
                        )}
                      >
                        <ChevronRightIcon />
                      </span>
                    ) : (
                      <span aria-hidden="true" className="size-3.5" />
                    )}
                  </button>

                  <button
                    type="button"
                    tabIndex={-1}
                    disabled={disabledOption}
                    className={cn(
                      "flex min-w-0 flex-1 items-center gap-2 rounded px-1.5 py-1 text-left outline-none",
                      selectable && !disabledOption
                        ? "cursor-pointer"
                        : row.expandable && !disabledOption
                          ? "cursor-pointer"
                          : "cursor-default",
                    )}
                    onClick={() => {
                      if (disabledOption) return;
                      if (selectable) {
                        selectOption(option);
                      } else if (!query.trim() && row.expandable) {
                        toggleExpanded(option.value);
                      }
                    }}
                  >
                    {option.icon ? (
                      <span className="flex size-4 shrink-0 items-center justify-center">
                        {option.icon}
                      </span>
                    ) : null}
                    <span className="min-w-0 flex-1 truncate" title={option.label}>
                      {option.label}
                    </span>
                    {selected ? (
                      <span className="ml-auto flex size-4 shrink-0 items-center justify-center text-[var(--yak-components-select-indicator)]">
                        <CheckIcon />
                      </span>
                    ) : null}
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}
