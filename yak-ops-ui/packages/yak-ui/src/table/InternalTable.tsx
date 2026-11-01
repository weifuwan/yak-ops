import { useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";

import { cn } from "../cn";
import { Empty } from "../empty";
import { Pagination } from "../pagination";
import { Skeleton } from "../skeleton";
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
import { resolveTableLayout } from "./layout";
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
  index: number,
  widths: number[] | null,
): CSSProperties => {
  const width = widths?.[index];
  if (width != null) return { width, minWidth: width, maxWidth: width };
  return {
    width: column.width,
    minWidth: column.width ?? column.minWidth,
    maxWidth: column.width,
  };
};

const toCssLength = (width: CSSProperties["width"]): string | undefined => {
  if (typeof width === "number") return `${width}px`;
  return typeof width === "string" ? width : undefined;
};

const getFixedOffset = <RecordType extends object>(
  columns: readonly TableColumn<RecordType>[],
  index: number,
  side: "left" | "right",
  resolvedWidths: number[] | null,
): CSSProperties["left"] => {
  const indexes =
    side === "left"
      ? Array.from({ length: index }, (_, offset) => offset)
      : Array.from({ length: columns.length - index - 1 }, (_, offset) => index + offset + 1);
  const offsets = indexes
    .filter((columnIndex) => columns[columnIndex]?.fixed === side)
    .map((columnIndex) => resolvedWidths?.[columnIndex] ?? toCssLength(columns[columnIndex]?.width))
    .filter((width): width is number | string => width != null);
  if (offsets.length === 0) return 0;
  if (offsets.every((width) => typeof width === "number")) {
    return (offsets as number[]).reduce((sum, width) => sum + width, 0);
  }
  return `calc(${offsets.map((width) => toCssLength(width) ?? width).join(" + ")})`;
};

const getCellStyle = <RecordType extends object>(
  column: TableColumn<RecordType>,
  index: number,
  columns: readonly TableColumn<RecordType>[],
  widths: number[] | null,
): CSSProperties => ({
  ...getColumnStyle(column, index, widths),
  ...(column.fixed === "left" ? { left: getFixedOffset(columns, index, "left", widths) } : {}),
  ...(column.fixed === "right" ? { right: getFixedOffset(columns, index, "right", widths) } : {}),
});

const fixedEdgeShadow = <RecordType extends object>(
  columns: readonly TableColumn<RecordType>[],
  index: number,
): string | undefined => {
  const column = columns[index];
  if (column?.fixed === "left" && columns[index + 1]?.fixed !== "left") {
    return "shadow-[4px_0_8px_-6px_rgba(16,24,40,0.24)]";
  }
  if (column?.fixed === "right" && columns[index - 1]?.fixed !== "right") {
    return "shadow-[-4px_0_8px_-6px_rgba(16,24,40,0.24)]";
  }
  return undefined;
};

const getCellTitle = (content: ReactNode): string | undefined =>
  typeof content === "string" || typeof content === "number" ? String(content) : undefined;

export function InternalTable<RecordType extends object>({
  bordered = false,
  className,
  columns,
  dataSource = [],
  emptyText,
  emptyClassName,
  footer,
  loading = false,
  onChange,
  onRow,
  pagination,
  rowHoverable = true,
  rowHeight,
  rowKey,
  rowSelection,
  scroll,
  size = "medium",
  sticky = false,
}: TableProps<RecordType>) {
  const viewportRef = useRef<HTMLDivElement>(null);
  const [viewportWidth, setViewportWidth] = useState(0);
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

  useLayoutEffect(() => {
    const viewport = viewportRef.current;
    if (!viewport) return;
    const measure = () => {
      const width = viewport.clientWidth;
      setViewportWidth((current) => (current === width ? current : width));
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(viewport);
    return () => observer.disconnect();
  }, []);

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
  const layout = resolveTableLayout(mergedColumns, viewportWidth, scroll?.x);
  const scrollStyle: CSSProperties | undefined =
    scroll?.y == null ? undefined : { maxHeight: scroll.y };
  const tableStyle: CSSProperties =
    layout.tableWidth != null
      ? { width: layout.tableWidth }
      : scroll?.x === true
        ? { minWidth: "max-content" }
        : scroll?.x == null
          ? {}
          : { minWidth: scroll.x };
  const fixedRowHeight = rowHeight != null && rowHeight > 0 ? rowHeight : undefined;
  const stickyHeader = sticky || scroll?.y != null;
  const sizeClass = sizeClasses[size];

  return (
    <div className={cn("flex w-full min-w-0 flex-col", className)}>
      <div className="relative min-h-0 shrink-0">
        <div
          ref={viewportRef}
          className={cn(
            "min-h-0 w-full min-w-0 overflow-x-auto bg-[var(--yak-components-table-bg)]",
            scroll?.y != null && "overflow-y-auto",
          )}
          style={scrollStyle}
        >
          <table
            aria-busy={loading || undefined}
            className={cn(
              "text-[var(--yak-components-table-text)]",
              bordered ? "border-separate border-spacing-0" : "border-collapse",
              layout.widths != null ? "table-fixed" : "w-full table-auto",
            )}
            style={tableStyle}
          >
            <colgroup>
              {mergedColumns.map((column, index) => (
                <col
                  key={getTableColumnKey(column, index)}
                  style={getColumnStyle(column, index, layout.widths)}
                />
              ))}
            </colgroup>

            <thead>
              <tr>
                {mergedColumns.map((column, index) => {
                  const align = column.align ?? "left";
                  const fixed = column.fixed != null;

                  return (
                    <th
                      key={getTableColumnKey(column, index)}
                      scope="col"
                      style={getCellStyle(column, index, mergedColumns, layout.widths)}
                      className={cn(
                        "bg-[var(--yak-components-table-header-bg)] font-medium text-[var(--yak-components-table-header-text)]",
                        sizeClass.header,
                        alignClasses[align],
                        (stickyHeader || fixed) && "sticky",
                        stickyHeader && "top-0",
                        fixed ? "z-30" : stickyHeader && "z-20",
                        scroll?.x != null && fixedEdgeShadow(mergedColumns, index),
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
                const selected = isSelected(record, rowIndex);

                return (
                  <tr
                    {...restRowProps}
                    key={resolveTableRowKey(record, rowIndex, rowKey)}
                    data-selected={selected ? "true" : undefined}
                    className={cn(
                      "bg-[var(--yak-components-table-row-bg)]",
                      "data-[selected=true]:bg-[var(--yak-color-hover)]",
                      rowHoverable && "transition-colors hover:bg-[var(--yak-color-hover)]",
                      rowClassName,
                    )}
                  >
                    {mergedColumns.map((column, columnIndex) => {
                      const value = column.dataIndex == null ? undefined : record[column.dataIndex];
                      const cell = column.render
                        ? column.render(value, record, rowIndex)
                        : (value as ReactNode);
                      const align = column.align ?? "left";
                      const fixed = column.fixed != null;

                      return (
                        <td
                          key={getTableColumnKey(column, columnIndex)}
                          style={{
                            ...getCellStyle(column, columnIndex, mergedColumns, layout.widths),
                            ...(fixedRowHeight != null
                              ? { height: fixedRowHeight, paddingTop: 0, paddingBottom: 0 }
                              : {}),
                          }}
                          className={cn(
                            "border-b border-[var(--yak-components-table-border)] align-middle",
                            sizeClass.cell,
                            alignClasses[align],
                            bordered && "border-r first:border-l",
                            bordered && rowIndex === 0 && "border-t",
                            fixed && "sticky z-10 bg-inherit",
                            scroll?.x != null && fixedEdgeShadow(mergedColumns, columnIndex),
                          )}
                        >
                          <div
                            className={cn(
                              "min-w-0",
                              column.ellipsis && "truncate",
                              fixedRowHeight != null &&
                                "flex w-full flex-col justify-center overflow-hidden",
                            )}
                            style={
                              fixedRowHeight != null
                                ? { height: Math.max(0, fixedRowHeight - 1) }
                                : undefined
                            }
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

              {data.length === 0 && loading
                ? Array.from({ length: 3 }, (_, rowIndex) => (
                    <tr key={`skeleton-${rowIndex}`} aria-hidden="true">
                      {mergedColumns.map((column, columnIndex) => (
                        <td
                          key={getTableColumnKey(column, columnIndex)}
                          style={{
                            ...getCellStyle(column, columnIndex, mergedColumns, layout.widths),
                            height: fixedRowHeight ?? 56,
                          }}
                          className={cn(
                            "border-b border-[var(--yak-components-table-border)] px-3",
                            bordered && "border-r first:border-l",
                            bordered && rowIndex === 0 && "border-t",
                            column.fixed != null &&
                              "sticky z-10 bg-[var(--yak-components-table-bg)]",
                          )}
                        >
                          <Skeleton
                            width={columnIndex === mergedColumns.length - 1 ? "65%" : "80%"}
                            height={12}
                          />
                        </td>
                      ))}
                    </tr>
                  ))
                : null}
              {data.length === 0 && !loading ? (
                <tr>
                  <td
                    colSpan={Math.max(mergedColumns.length, 1)}
                    className={cn(
                      "border-b border-[var(--yak-components-table-border)]",
                      bordered && "border-x border-t",
                    )}
                  >
                    <Empty
                      className={cn("min-h-48", emptyClassName)}
                      description={emptyText ?? "No data"}
                    />
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </div>

      {footer || resolvedPagination ? (
        <div className="mt-3 flex min-h-12 shrink-0 flex-nowrap items-center gap-4 px-4">
          {footer ? <div className="min-w-0 flex-1">{footer}</div> : null}
          {resolvedPagination ? (
            <Pagination
              className="ml-auto shrink-0"
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
