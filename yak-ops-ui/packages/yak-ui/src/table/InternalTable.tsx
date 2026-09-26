import { useRef, type CSSProperties, type ReactNode } from "react";

import { cn } from "../cn";
import { Empty } from "../empty";
import { Pagination } from "../pagination";
import { Spinner } from "../spinner";
import { useFilter } from "./hooks/useFilter";
import { usePagination } from "./hooks/usePagination";
import { useSelection } from "./hooks/useSelection";
import { useSorter } from "./hooks/useSorter";
import type {
  TableAlign,
  TableChangeAction,
  TableColumn,
  TableFilters,
  TablePaginationConfig,
  TablePaginationState,
  TableProps,
  TableSize,
  TableSorterResult,
} from "./interface";
import { getTableColumnKey, resolveTableRowKey } from "./utils";

const sizeClasses: Record<TableSize, { header: string; cell: string }> = {
  small: {
    header: "h-8 px-3 text-xs",
    cell: "px-3 py-1.5 text-xs",
  },
  medium: {
    header: "h-10 px-3 text-xs",
    cell: "px-3 py-2.5 text-[13px]",
  },
  large: {
    header: "h-12 px-4 text-[13px]",
    cell: "px-4 py-3 text-sm",
  },
};

const alignClasses: Record<TableAlign, string> = {
  left: "text-left",
  center: "text-center",
  right: "text-right",
};

const getColumnStyle = <RecordType extends object>(
  column: TableColumn<RecordType>,
): CSSProperties => ({
  width: column.width,
  minWidth: column.minWidth,
});

const getCellTitle = (content: ReactNode): string | undefined =>
  typeof content === "string" || typeof content === "number" ? String(content) : undefined;

export function InternalTable<RecordType extends object>({
  bordered = false,
  className,
  columns,
  dataSource = [],
  emptyText,
  footer,
  loading = false,
  onChange,
  onRow,
  pagination,
  rowHoverable = true,
  rowKey,
  rowSelection,
  scroll,
  size = "medium",
  sticky = false,
}: TableProps<RecordType>) {
  const filtersRef = useRef<TableFilters>({});
  const sorterRef = useRef<TableSorterResult<RecordType>>({});
  const paginationStateRef = useRef<TablePaginationState | false>(false);
  const filterDataRef = useRef<
    (data: readonly RecordType[], filtersOverride?: TableFilters) => readonly RecordType[]
  >((data) => data);
  const sortDataRef = useRef<
    (
      data: readonly RecordType[],
      sorterOverride?: TableSorterResult<RecordType>,
    ) => readonly RecordType[]
  >((data) => data);

  const emitChange = (
    action: TableChangeAction,
    nextFilters: TableFilters,
    nextSorter: TableSorterResult<RecordType>,
    paginationOverride?: Pick<TablePaginationState, "current" | "pageSize">,
  ) => {
    if (!onChange) return;

    const currentDataSource = sortDataRef.current(
      filterDataRef.current(dataSource, nextFilters),
      nextSorter,
    );

    let nextPagination = paginationStateRef.current;
    if (nextPagination !== false) {
      nextPagination = {
        ...nextPagination,
        current: paginationOverride?.current ?? nextPagination.current,
        pageSize: paginationOverride?.pageSize ?? nextPagination.pageSize,
        total:
          pagination !== false && pagination?.total != null
            ? pagination.total
            : currentDataSource.length,
      };
    }

    onChange(nextPagination, nextFilters, nextSorter, {
      action,
      currentDataSource,
    });
  };

  const sorterState = useSorter(columns, (nextSorter) => {
    emitChange("sort", filtersRef.current, nextSorter);
  });
  sorterRef.current = sorterState.sorter;
  sortDataRef.current = sorterState.sortData;

  const filterState = useFilter(sorterState.columns, (nextFilters) => {
    emitChange("filter", nextFilters, sorterRef.current);
  });
  filtersRef.current = filterState.filters;
  filterDataRef.current = filterState.filterData;

  const processedData = sorterState.sortData(filterState.filterData(dataSource));

  const mergedPagination: false | TablePaginationConfig | undefined =
    pagination === false || pagination == null
      ? pagination
      : {
          ...pagination,
          onChange: (page, pageSize) => {
            pagination.onChange?.(page, pageSize);
            emitChange("paginate", filtersRef.current, sorterRef.current, {
              current: page,
              pageSize,
            });
          },
        };

  const { data, pagination: resolvedPagination } = usePagination(mergedPagination, processedData);
  paginationStateRef.current =
    resolvedPagination == null
      ? false
      : {
          current: resolvedPagination.page,
          pageSize: resolvedPagination.pageSize,
          total: resolvedPagination.total,
        };

  const { columns: mergedColumns, isSelected } = useSelection(
    rowSelection,
    data,
    filterState.columns,
    rowKey,
  );
  const tableLayoutFixed = mergedColumns.some((column) => column.ellipsis);
  const scrollStyle: CSSProperties | undefined =
    scroll?.y == null ? undefined : { maxHeight: scroll.y };
  const tableStyle: CSSProperties =
    scroll?.x === true
      ? { minWidth: "max-content" }
      : scroll?.x == null
        ? {}
        : { minWidth: scroll.x };
  const sizeClass = sizeClasses[size];

  return (
    <div className={cn("flex min-w-0 flex-col", className)}>
      <div
        className={cn(
          "relative overflow-x-auto bg-[var(--yak-components-table-bg)]",
          scroll?.y != null && "overflow-y-auto",
          bordered && "border border-[var(--yak-components-table-border-strong)]",
        )}
        style={scrollStyle}
      >
        <table
          aria-busy={loading || undefined}
          className={cn(
            "w-full border-collapse text-[var(--yak-components-table-text)]",
            tableLayoutFixed ? "table-fixed" : "table-auto",
          )}
          style={tableStyle}
        >
          <colgroup>
            {mergedColumns.map((column, index) => (
              <col key={getTableColumnKey(column, index)} style={getColumnStyle(column)} />
            ))}
          </colgroup>

          <thead>
            <tr>
              {mergedColumns.map((column, index) => {
                const align = column.align ?? "left";

                return (
                  <th
                    key={getTableColumnKey(column, index)}
                    scope="col"
                    style={getColumnStyle(column)}
                    className={cn(
                      "bg-[var(--yak-components-table-header-bg)] font-medium text-[var(--yak-components-table-header-text)]",
                      sizeClass.header,
                      alignClasses[align],
                      sticky && "sticky top-0 z-10",
                    )}
                  >
                    <div className={cn("min-w-0", column.ellipsis && "truncate")}>
                      {column.title}
                    </div>
                  </th>
                );
              })}
            </tr>
          </thead>

          <tbody>
            {data.map((record, rowIndex) => {
              const rowProps = onRow?.(record, rowIndex) ?? {};
              const { className: rowClassName, ...restRowProps } = rowProps;

              return (
                <tr
                  {...restRowProps}
                  key={resolveTableRowKey(record, rowIndex, rowKey)}
                  className={cn(
                    "border-b border-[var(--yak-components-table-border)] bg-[var(--yak-components-table-row-bg)] last:border-b-0",
                    rowHoverable &&
                      "transition-colors hover:bg-[var(--yak-components-table-row-bg-hover)]",
                    isSelected(record, rowIndex) &&
                      "bg-[var(--yak-components-table-row-bg-selected)] hover:bg-[var(--yak-components-table-row-bg-selected-hover)]",
                    rowClassName,
                  )}
                >
                  {mergedColumns.map((column, columnIndex) => {
                    const value = column.dataIndex == null ? undefined : record[column.dataIndex];
                    const cell = column.render
                      ? column.render(value, record, rowIndex)
                      : (value as ReactNode);
                    const align = column.align ?? "left";

                    return (
                      <td
                        key={getTableColumnKey(column, columnIndex)}
                        style={getColumnStyle(column)}
                        className={cn(
                          "align-middle",
                          sizeClass.cell,
                          alignClasses[align],
                          bordered &&
                            "border-r border-[var(--yak-components-table-border)] last:border-r-0",
                        )}
                      >
                        <div
                          className={cn("min-w-0", column.ellipsis && "truncate")}
                          title={column.ellipsis ? getCellTitle(cell) : undefined}
                        >
                          {cell ?? null}
                        </div>
                      </td>
                    );
                  })}
                </tr>
              );
            })}

            {data.length === 0 ? (
              <tr>
                <td colSpan={Math.max(mergedColumns.length, 1)}>
                  <Empty className="min-h-48" description={emptyText ?? "No data"} />
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>

        {loading ? (
          <div className="absolute inset-0 z-20 flex items-center justify-center bg-[var(--yak-components-table-loading-bg)]">
            <Spinner size="large" label="Loading table" />
          </div>
        ) : null}
      </div>

      {footer || resolvedPagination ? (
        <div
          className={cn(
            "mt-auto flex flex-nowrap items-center gap-3",
            footer
              ? "border-t border-[var(--yak-components-table-border-strong)] py-3"
              : "justify-end pt-3",
          )}
        >
          {footer ? <div className="min-w-0 shrink-0">{footer}</div> : null}
          {resolvedPagination ? (
            <Pagination
              className="ml-auto"
              page={resolvedPagination.page}
              pageSize={resolvedPagination.pageSize}
              total={resolvedPagination.total}
              pageSizeOptions={resolvedPagination.pageSizeOptions}
              pageSizeLabel={resolvedPagination.pageSizeLabel}
              disabled={loading || resolvedPagination.disabled}
              showSizeChanger={resolvedPagination.showSizeChanger}
              showQuickJumper={resolvedPagination.showQuickJumper}
              renderTotal={resolvedPagination.renderTotal}
              onChange={resolvedPagination.onChange}
            />
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
