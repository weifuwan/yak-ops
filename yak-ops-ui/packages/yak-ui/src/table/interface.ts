import type { CSSProperties, HTMLAttributes, Key, ReactNode } from "react";

import type { CheckboxProps } from "../checkbox";

export type TableSize = "small" | "medium" | "large";

export type TableAlign = "left" | "center" | "right";

export interface TableColumn<RecordType extends object> {
  key?: Key;
  title?: ReactNode;
  dataIndex?: keyof RecordType;
  width?: CSSProperties["width"];
  minWidth?: CSSProperties["minWidth"];
  align?: TableAlign;
  ellipsis?: boolean;
  render?: (value: unknown, record: RecordType, index: number) => ReactNode;
}

export type TableColumns<RecordType extends object> = TableColumn<RecordType>[];

export interface TablePaginationConfig {
  current?: number;
  pageSize?: number;
  total?: number;
  pageSizeOptions?: readonly number[];
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
  y?: CSSProperties["maxHeight"];
}

export interface TableProps<RecordType extends object> {
  columns: TableColumns<RecordType>;
  dataSource?: readonly RecordType[];
  rowKey?: keyof RecordType | ((record: RecordType) => Key);
  loading?: boolean;
  pagination?: false | TablePaginationConfig;
  rowSelection?: TableRowSelection<RecordType>;
  size?: TableSize;
  bordered?: boolean;
  sticky?: boolean;
  scroll?: TableScroll;
  emptyText?: ReactNode;
  rowHoverable?: boolean;
  className?: string;
  onRow?: (record: RecordType, index: number) => HTMLAttributes<HTMLTableRowElement>;
}
