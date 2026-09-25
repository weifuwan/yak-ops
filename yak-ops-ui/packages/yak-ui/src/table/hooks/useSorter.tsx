import { useState, type Key } from "react";

import type {
  TableColumn,
  TableColumns,
  TableSorterResult,
  TableSortOrder,
} from "../interface";
import { getTableColumnKey } from "../utils";

const DEFAULT_SORT_DIRECTIONS = ["ascend", "descend"] as const;

interface InternalSorterState {
  columnKey?: Key;
  order: TableSortOrder;
}

export interface TableSorterHookResult<RecordType extends object> {
  columns: TableColumns<RecordType>;
  sorter: TableSorterResult<RecordType>;
  sortData: (
    data: readonly RecordType[],
    sorterOverride?: TableSorterResult<RecordType>,
  ) => readonly RecordType[];
}

const getInitialSorterState = <RecordType extends object>(
  columns: TableColumns<RecordType>,
): InternalSorterState => {
  const index = columns.findIndex((column) => column.sorter && column.defaultSortOrder);
  if (index < 0) return { order: null };

  return {
    columnKey: getTableColumnKey(columns[index], index),
    order: columns[index].defaultSortOrder ?? null,
  };
};

const getNextOrder = <RecordType extends object>(
  column: TableColumn<RecordType>,
  currentOrder: TableSortOrder,
): TableSortOrder => {
  const directions = column.sortDirections ?? DEFAULT_SORT_DIRECTIONS;
  if (currentOrder == null) return directions[0] ?? null;

  const index = directions.indexOf(currentOrder);
  return index >= 0 && index < directions.length - 1 ? directions[index + 1] : null;
};

export function useSorter<RecordType extends object>(
  columns: TableColumns<RecordType>,
  onSorterChange?: (sorter: TableSorterResult<RecordType>) => void,
): TableSorterHookResult<RecordType> {
  const [innerSorter, setInnerSorter] = useState<InternalSorterState>(() =>
    getInitialSorterState(columns),
  );

  const controlledIndex = columns.findIndex(
    (column) => column.sorter && column.sortOrder !== undefined,
  );
  const controlledColumn = controlledIndex >= 0 ? columns[controlledIndex] : undefined;
  const controlledKey =
    controlledColumn == null ? undefined : getTableColumnKey(controlledColumn, controlledIndex);

  const activeKey = controlledColumn == null ? innerSorter.columnKey : controlledKey;
  const activeOrder = controlledColumn == null ? innerSorter.order : controlledColumn.sortOrder ?? null;

  const activeIndex = columns.findIndex(
    (column, index) => column.sorter && getTableColumnKey(column, index) === activeKey,
  );
  const activeColumn = activeIndex >= 0 ? columns[activeIndex] : undefined;

  const sorter: TableSorterResult<RecordType> =
    activeColumn == null
      ? {}
      : {
          columnKey: activeKey,
          field: activeColumn.dataIndex,
          order: activeOrder,
          column: activeColumn,
        };

  const mergedColumns = columns.map((column, index) => {
    if (!column.sorter) return column;

    const columnKey = getTableColumnKey(column, index);
    const currentOrder = columnKey === activeKey ? activeOrder : null;

    return {
      ...column,
      title: (
        <button
          type="button"
          aria-label="Sort column"
          className="group inline-flex max-w-full cursor-pointer items-center gap-1.5 border-0 bg-transparent p-0 text-inherit outline-none focus-visible:ring-2 focus-visible:ring-[var(--yak-components-table-focus-ring)]"
          onClick={() => {
            const nextOrder = getNextOrder(column, currentOrder);
            if (column.sortOrder === undefined) {
              setInnerSorter({ columnKey, order: nextOrder });
            }
            onSorterChange?.({
              columnKey,
              field: column.dataIndex,
              order: nextOrder,
              column,
            });
          }}
        >
          <span className="min-w-0 truncate">{column.title}</span>
          <span
            aria-hidden="true"
            className="flex shrink-0 flex-col text-[8px] leading-[7px] text-[var(--yak-components-table-sorter-inactive)]"
          >
            <span
              className={
                currentOrder === "ascend"
                  ? "text-[var(--yak-components-table-sorter-active)]"
                  : undefined
              }
            >
              ▲
            </span>
            <span
              className={
                currentOrder === "descend"
                  ? "text-[var(--yak-components-table-sorter-active)]"
                  : undefined
              }
            >
              ▼
            </span>
          </span>
        </button>
      ),
    };
  });

  const sortData = (
    data: readonly RecordType[],
    sorterOverride?: TableSorterResult<RecordType>,
  ): readonly RecordType[] => {
    const resolvedSorter = sorterOverride ?? sorter;
    const resolvedColumn =
      resolvedSorter.column ??
      columns.find(
        (column, index) =>
          getTableColumnKey(column, index) === resolvedSorter.columnKey,
      );
    const resolvedOrder = resolvedSorter.order ?? null;

    if (
      resolvedColumn == null ||
      resolvedOrder == null ||
      typeof resolvedColumn.sorter !== "function"
    ) {
      return data;
    }

    const factor = resolvedOrder === "ascend" ? 1 : -1;
    return [...data].sort((a, b) => resolvedColumn.sorter!(a, b) * factor);
  };

  return {
    columns: mergedColumns,
    sorter,
    sortData,
  };
}
