import type { Key } from "react";

import type { TableColumn, TableProps } from "./interface";

export const TABLE_SELECTION_COLUMN_KEY = "__yak_table_selection__";

export const stringifyTableColumnKey = (key: Key): string => String(key);

export const normalizeTableKey = (value: unknown, fallback: number): Key => {
  if (typeof value === "string" || typeof value === "number" || typeof value === "bigint") {
    return value;
  }

  if (value != null) return String(value);
  return fallback;
};

export const resolveTableRowKey = <RecordType extends object>(
  record: RecordType,
  index: number,
  rowKey: TableProps<RecordType>["rowKey"],
): Key => {
  if (typeof rowKey === "function") return rowKey(record);

  if (rowKey != null) return normalizeTableKey(record[rowKey], index);

  const defaultKey = (record as unknown as Record<PropertyKey, unknown>).key;
  return normalizeTableKey(defaultKey, index);
};

export const getTableColumnKey = <RecordType extends object>(
  column: TableColumn<RecordType>,
  index: number,
): Key => column.key ?? String(column.dataIndex ?? index);
