import { useMemo, useState, type Key, type MouseEvent } from "react";

import { Checkbox } from "../../checkbox";
import type {
  TableColumn,
  TableColumns,
  TableProps,
  TableRowSelection,
  TableRowSelectionCheckboxProps,
} from "../interface";
import {
  TABLE_SELECTION_COLUMN_KEY,
  resolveTableRowKey,
} from "../utils";

const DEFAULT_SELECTION_COLUMN_WIDTH = 48;

interface SelectionRow<RecordType extends object> {
  key: Key;
  record: RecordType;
  checkboxProps: TableRowSelectionCheckboxProps;
}

export interface TableSelectionResult<RecordType extends object> {
  columns: TableColumns<RecordType>;
  isSelected: (record: RecordType, index: number) => boolean;
}

export function useSelection<RecordType extends object>(
  rowSelection: TableRowSelection<RecordType> | undefined,
  data: readonly RecordType[],
  columns: TableColumns<RecordType>,
  rowKey: TableProps<RecordType>["rowKey"],
): TableSelectionResult<RecordType> {
  const [innerSelectedRowKeys, setInnerSelectedRowKeys] = useState<Key[]>(
    () => [...(rowSelection?.defaultSelectedRowKeys ?? [])],
  );

  const selectedRowKeys = rowSelection?.selectedRowKeys ?? innerSelectedRowKeys;
  const selectedKeySet = useMemo(() => new Set<Key>(selectedRowKeys), [selectedRowKeys]);

  const rows = useMemo<SelectionRow<RecordType>[]>(
    () =>
      data.map((record, index) => ({
        key: resolveTableRowKey(record, index, rowKey),
        record,
        checkboxProps: rowSelection?.getCheckboxProps?.(record) ?? {},
      })),
    [data, rowKey, rowSelection],
  );

  if (!rowSelection) {
    return {
      columns,
      isSelected: () => false,
    };
  }

  const selectableRows = rows.filter((row) => !row.checkboxProps.disabled);
  const allSelected =
    selectableRows.length > 0 && selectableRows.every((row) => selectedKeySet.has(row.key));
  const someSelected = selectableRows.some((row) => selectedKeySet.has(row.key));

  const emitChange = (nextSelectedRowKeys: Key[]) => {
    if (rowSelection.selectedRowKeys == null) setInnerSelectedRowKeys(nextSelectedRowKeys);

    const currentRowsByKey = new Map(rows.map((row) => [row.key, row.record]));
    const selectedRows = nextSelectedRowKeys
      .map((key) => currentRowsByKey.get(key))
      .filter((record): record is RecordType => record != null);

    rowSelection.onChange?.(nextSelectedRowKeys, selectedRows);
  };

  const toggleRow = (key: Key, checked: boolean) => {
    const nextSelectedKeys = new Set<Key>(selectedRowKeys);
    if (checked) nextSelectedKeys.add(key);
    else nextSelectedKeys.delete(key);
    emitChange([...nextSelectedKeys]);
  };

  const toggleAll = (checked: boolean) => {
    const nextSelectedKeys = new Set<Key>(selectedRowKeys);
    for (const row of selectableRows) {
      if (checked) nextSelectedKeys.add(row.key);
      else nextSelectedKeys.delete(row.key);
    }
    emitChange([...nextSelectedKeys]);
  };

  const stopPropagation = (event: MouseEvent) => event.stopPropagation();

  const selectionColumn: TableColumn<RecordType> = {
    key: TABLE_SELECTION_COLUMN_KEY,
    width: rowSelection.columnWidth ?? DEFAULT_SELECTION_COLUMN_WIDTH,
    align: "center",
    title: rowSelection.hideSelectAll ? null : (
      <div className="flex items-center justify-center" onClick={stopPropagation}>
        <Checkbox
          aria-label="Select all rows"
          checked={allSelected}
          indeterminate={!allSelected && someSelected}
          disabled={selectableRows.length === 0}
          onCheckedChange={toggleAll}
        />
      </div>
    ),
    render: (_value, record, index) => {
      const key = resolveTableRowKey(record, index, rowKey);
      const checkboxProps = rowSelection.getCheckboxProps?.(record) ?? {};

      return (
        <div className="flex items-center justify-center" onClick={stopPropagation}>
          <Checkbox
            {...checkboxProps}
            aria-label={checkboxProps["aria-label"] ?? "Select row"}
            checked={selectedKeySet.has(key)}
            onCheckedChange={(checked) => toggleRow(key, checked)}
          />
        </div>
      );
    },
  };

  return {
    columns: [selectionColumn, ...columns],
    isSelected: (record, index) => selectedKeySet.has(resolveTableRowKey(record, index, rowKey)),
  };
}
