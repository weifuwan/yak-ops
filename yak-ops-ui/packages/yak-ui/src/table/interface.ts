import type { CSSProperties, HTMLAttributes, Key, ReactNode } from "react";

import type { CheckboxProps } from "../checkbox";

export type TableSize = "small" | "medium" | "large";

export type TableAlign = "left" | "center" | "right";

export type TableFixed = "left" | "right";

export type TableSortOrder = "ascend" | "descend" | null;

export type TableSortDirection = Exclude<TableSortOrder, null>;

export type TableChangeAction = "paginate" | "sort" | "filter";

export type TableFilterValue = readonly Key[] | null;

export type TableFilters = Record<string, TableFilterValue>;

export interface TableFilterItem {
  text: ReactNode;
  value: Key;
  disabled?: boolean;
}

export type TableSorterCompare<RecordType extends object> = (
  a: RecordType,
  b: RecordType,
) => number;

export interface TableSorterResult<RecordType extends object> {
  columnKey?: Key;
  field?: keyof RecordType;
  order?: TableSortOrder;
  column?: TableColumn<RecordType>;
}

export interface TablePaginationState {
  current: number;
  pageSize: number;
  total: number;
}

export interface TableChangeExtra<RecordType extends object> {
  action: TableChangeAction;
  currentDataSource: readonly RecordType[];
}

export interface TableColumn<RecordType extends object> {
  key?: Key;
  title?: ReactNode;
  dataIndex?: keyof RecordType;
  width?: CSSProperties["width"];
  minWidth?: CSSProperties["minWidth"];
  align?: TableAlign;
  /** 横向滚动时固定在视口左侧或右侧；固定列必须提供明确 width。 */
  fixed?: TableFixed;
  ellipsis?: boolean;
  sorter?: boolean | TableSorterCompare<RecordType>;
  sortOrder?: TableSortOrder;
  defaultSortOrder?: TableSortDirection;
  sortDirections?: readonly TableSortDirection[];
  filters?: readonly TableFilterItem[];
  filteredValue?: TableFilterValue;
  defaultFilteredValue?: readonly Key[];
  filterMultiple?: boolean;
  onFilter?: (value: Key, record: RecordType) => boolean;
  render?: (value: unknown, record: RecordType, index: number) => ReactNode;
}

export type TableColumns<RecordType extends object> = TableColumn<RecordType>[];

export interface TablePaginationConfig {
  current?: number;
  pageSize?: number;
  total?: number;
  pageSizeOptions?: readonly number[];
  pageSizeLabel?: ReactNode;
  disabled?: boolean;
  showSizeChanger?: boolean;
  showQuickJumper?: boolean;
  renderTotal?: (total: number, range: [number, number]) => ReactNode;
  onChange?: (page: number, pageSize: number) => void;
}

export type TableRowSelectionCheckboxProps = Omit<
  CheckboxProps,
  "checked" | "defaultChecked" | "indeterminate" | "onCheckedChange"
>;

export interface TableRowSelection<RecordType extends object> {
  selectedRowKeys?: readonly Key[];
  defaultSelectedRowKeys?: readonly Key[];
  columnWidth?: CSSProperties["width"];
  hideSelectAll?: boolean;
  getCheckboxProps?: (record: RecordType) => TableRowSelectionCheckboxProps;
  onChange?: (selectedRowKeys: Key[], selectedRows: RecordType[]) => void;
}

export interface TableScroll {
  x?: CSSProperties["minWidth"] | true;
  /** 滚动视口的最大高度；数据较少时仍按内容高度展示。 */
  y?: CSSProperties["maxHeight"];
}

export interface TableProps<RecordType extends object> {
  columns: TableColumns<RecordType>;
  dataSource?: readonly RecordType[];
  rowKey?: keyof RecordType | ((record: RecordType) => Key);
  loading?: boolean;
  /** Exact body row height in pixels. Long cell content is clipped. */
  rowHeight?: number;
  pagination?: false | TablePaginationConfig;
  footer?: ReactNode;
  rowSelection?: TableRowSelection<RecordType>;
  size?: TableSize;
  /** 仅数据区绘制完整网格，不为表头、滚动容器或底栏添加外框。 */
  bordered?: boolean;
  sticky?: boolean;
  scroll?: TableScroll;
  emptyText?: ReactNode;
  /** Styles the empty body independently of the table's row density. */
  emptyClassName?: string;
  rowHoverable?: boolean;
  className?: string;
  onChange?: (
    pagination: TablePaginationState | false,
    filters: TableFilters,
    sorter: TableSorterResult<RecordType>,
    extra: TableChangeExtra<RecordType>,
  ) => void;
  onRow?: (record: RecordType, index: number) => HTMLAttributes<HTMLTableRowElement>;
}
