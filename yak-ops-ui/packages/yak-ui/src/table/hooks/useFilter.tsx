import { useEffect, useState, type Key } from "react";

import { Button } from "../../button";
import { Checkbox } from "../../checkbox";
import { Popover, PopoverContent, PopoverTrigger } from "../../popover";
import type {
  TableColumn,
  TableColumns,
  TableFilterItem,
  TableFilters,
  TableFilterValue,
} from "../interface";
import { getTableColumnKey, stringifyTableColumnKey } from "../utils";

interface TableFilterControlProps {
  filters: readonly TableFilterItem[];
  selectedValues: readonly Key[];
  multiple: boolean;
  onConfirm: (values: readonly Key[]) => void;
}

function TableFilterControl({
  filters,
  multiple,
  onConfirm,
  selectedValues,
}: TableFilterControlProps) {
  const [open, setOpen] = useState(false);
  const [draftValues, setDraftValues] = useState<readonly Key[]>(selectedValues);

  useEffect(() => {
    if (open) setDraftValues(selectedValues);
  }, [open, selectedValues]);

  const active = selectedValues.length > 0;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        aria-label="Filter column"
        className={[
          "inline-flex size-6 shrink-0 cursor-pointer items-center justify-center rounded-md border-0 bg-transparent outline-none transition-colors",
          "hover:bg-[var(--yak-components-table-filter-bg-hover)] focus-visible:ring-2 focus-visible:ring-[var(--yak-components-table-focus-ring)]",
          active
            ? "text-[var(--yak-components-table-filter-active)]"
            : "text-[var(--yak-components-table-filter-inactive)]",
        ].join(" ")}
      >
        <svg aria-hidden="true" viewBox="0 0 16 16" className="size-3.5" fill="none">
          <path d="M2.5 3.25h11L9.25 8v3.35l-2.5 1.4V8L2.5 3.25Z" fill="currentColor" />
        </svg>
      </PopoverTrigger>

      <PopoverContent align="end" sideOffset={6} className="w-52 p-2">
        <div className="max-h-60 overflow-y-auto py-1">
          {filters.map((item) => {
            const checked = draftValues.includes(item.value);

            return (
              <label
                key={String(item.value)}
                className={[
                  "flex min-h-8 items-center gap-2 rounded-lg px-2 text-[13px]",
                  item.disabled
                    ? "cursor-not-allowed opacity-45"
                    : "cursor-pointer hover:bg-[var(--yak-components-menu-item-hover)]",
                ].join(" ")}
              >
                <Checkbox
                  checked={checked}
                  disabled={item.disabled}
                  onCheckedChange={(nextChecked) => {
                    if (multiple) {
                      setDraftValues((current) =>
                        nextChecked
                          ? [...current, item.value]
                          : current.filter((value) => value !== item.value),
                      );
                      return;
                    }

                    setDraftValues(nextChecked ? [item.value] : []);
                  }}
                />
                <span className="min-w-0 flex-1 truncate">{item.text}</span>
              </label>
            );
          })}
        </div>

        <div className="mt-1 flex items-center justify-between border-t border-[var(--yak-components-control-border)] pt-2">
          <Button size="small" variant="ghost" onClick={() => setDraftValues([])}>
            Reset
          </Button>
          <Button
            size="small"
            variant="primary"
            onClick={() => {
              onConfirm(draftValues);
              setOpen(false);
            }}
          >
            Apply
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}

const getInitialFilterState = <RecordType extends object>(
  columns: TableColumns<RecordType>,
): Record<string, readonly Key[]> => {
  const result: Record<string, readonly Key[]> = {};

  columns.forEach((column, index) => {
    if (!column.filters || column.defaultFilteredValue == null) return;
    result[stringifyTableColumnKey(getTableColumnKey(column, index))] = column.defaultFilteredValue;
  });

  return result;
};

export interface TableFilterHookResult<RecordType extends object> {
  columns: TableColumns<RecordType>;
  filters: TableFilters;
  filterData: (
    data: readonly RecordType[],
    filtersOverride?: TableFilters,
  ) => readonly RecordType[];
}

export function useFilter<RecordType extends object>(
  columns: TableColumns<RecordType>,
  onFiltersChange?: (filters: TableFilters) => void,
): TableFilterHookResult<RecordType> {
  const [innerFilters, setInnerFilters] = useState<Record<string, readonly Key[]>>(() =>
    getInitialFilterState(columns),
  );

  const getColumnFilterValue = (column: TableColumn<RecordType>, index: number): readonly Key[] => {
    if (column.filteredValue !== undefined) return column.filteredValue ?? [];
    return innerFilters[stringifyTableColumnKey(getTableColumnKey(column, index))] ?? [];
  };

  const buildFilters = (overrideKey?: string, overrideValue?: readonly Key[]): TableFilters => {
    const result: TableFilters = {};

    columns.forEach((column, index) => {
      if (!column.filters) return;

      const key = stringifyTableColumnKey(getTableColumnKey(column, index));
      const value =
        key === overrideKey ? (overrideValue ?? []) : getColumnFilterValue(column, index);
      result[key] = value.length > 0 ? value : null;
    });

    return result;
  };

  const filters = buildFilters();

  const mergedColumns = columns.map((column, index) => {
    if (!column.filters || column.filters.length === 0) return column;

    const columnKey = stringifyTableColumnKey(getTableColumnKey(column, index));
    const selectedValues = getColumnFilterValue(column, index);

    return {
      ...column,
      title: (
        <div className="flex min-w-0 items-center justify-between gap-1">
          <div className="min-w-0">{column.title}</div>
          <TableFilterControl
            filters={column.filters}
            selectedValues={selectedValues}
            multiple={column.filterMultiple !== false}
            onConfirm={(nextValues) => {
              if (column.filteredValue === undefined) {
                setInnerFilters((current) => ({
                  ...current,
                  [columnKey]: nextValues,
                }));
              }

              onFiltersChange?.(buildFilters(columnKey, nextValues));
            }}
          />
        </div>
      ),
    };
  });

  const filterData = (
    data: readonly RecordType[],
    filtersOverride?: TableFilters,
  ): readonly RecordType[] =>
    columns.reduce<readonly RecordType[]>((currentData, column, index) => {
      if (!column.filters || typeof column.onFilter !== "function") return currentData;

      const columnKey = stringifyTableColumnKey(getTableColumnKey(column, index));
      const overrideValue = filtersOverride?.[columnKey];
      const selectedValues =
        filtersOverride && columnKey in filtersOverride
          ? (overrideValue ?? [])
          : getColumnFilterValue(column, index);
      if (selectedValues.length === 0) return currentData;

      return currentData.filter((record) =>
        selectedValues.some((value) => column.onFilter!(value, record)),
      );
    }, data);

  return {
    columns: mergedColumns,
    filters,
    filterData,
  };
}
