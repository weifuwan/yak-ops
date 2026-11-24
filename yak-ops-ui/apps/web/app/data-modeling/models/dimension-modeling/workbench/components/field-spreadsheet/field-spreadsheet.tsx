import { useEffect, useMemo, useRef, useState } from "react";
import type { ClipboardEvent, KeyboardEvent } from "react";
import { Button, Checkbox, Input, toast } from "@yak-ops/yak-ui";
import { Trash2 } from "lucide-react";

import type { DimensionFieldRecord } from "@/service/data-modeling/models/dimension-modeling";
import FieldGridSelect from "./field-grid-select";
import FieldSpreadsheetToolbar from "./field-spreadsheet-toolbar";
import {
  cellClipboardValue,
  createDraftField,
  EDITABLE_FIELD_COLUMNS,
  fieldRowId,
  isEmptyDimensionField,
  patchFromClipboard,
  validateDimensionFields,
} from "./field-grid-utils";
import {
  defaultVisibleFieldColumns,
  FIELD_CATEGORY_OPTIONS,
  fieldGridColumns,
  type FieldColumnKey,
  type FieldGridColumn,
  type FieldSpreadsheetProps,
} from "./field-grid-types";

type ActiveCell = {
  rowId: string;
  column: FieldColumnKey;
};

function gridTemplate(columns: FieldGridColumn[]) {
  return columns
    .map((column) =>
      column.flex
        ? `minmax(${column.minWidth}px, ${column.flex}fr)`
        : `${column.width ?? column.minWidth}px`,
    )
    .join(" ");
}

function gridMinWidth(columns: FieldGridColumn[]) {
  return columns.reduce(
    (total, column) => total + (column.width ?? column.minWidth),
    0,
  );
}

function cellKey(rowId: string, column: FieldColumnKey) {
  return rowId + "::" + column;
}

export function FieldSpreadsheet({
  modelType,
  fieldType,
  fields,
  fieldStandardOptions,
  codeStandardOptions,
  unitOptions,
  fieldTypeOptions,
  loading = false,
  error = false,
  onChange,
}: FieldSpreadsheetProps) {
  const [visibleColumns, setVisibleColumns] = useState<Set<FieldColumnKey>>(
    () => defaultVisibleFieldColumns(modelType, fieldType),
  );
  const [insertCount, setInsertCount] = useState("1");
  const [activeCell, setActiveCell] = useState<ActiveCell | null>(null);
  const cellRefs = useRef(new Map<string, HTMLDivElement>());

  const availableColumns = useMemo(
    () => fieldGridColumns(modelType, fieldType),
    [modelType, fieldType],
  );
  const columns = useMemo(
    () =>
      availableColumns.filter(
        (column) => !column.configurable || visibleColumns.has(column.key),
      ),
    [availableColumns, visibleColumns],
  );
  const templateColumns = useMemo(() => gridTemplate(columns), [columns]);
  const minGridWidth = useMemo(() => gridMinWidth(columns), [columns]);

  useEffect(() => {
    setVisibleColumns(defaultVisibleFieldColumns(modelType, fieldType));
  }, [modelType, fieldType]);
  const editableColumns = useMemo(
    () =>
      columns.filter((column) =>
        EDITABLE_FIELD_COLUMNS.includes(column.key),
      ),
    [columns],
  );
  const validationIssues = useMemo(() => validateDimensionFields(fields), [fields]);
  const issuesByCell = useMemo(() => {
    const result = new Map<string, string>();
    validationIssues.forEach((issue) => {
      result.set(cellKey(issue.rowId, issue.column), issue.message);
    });
    return result;
  }, [validationIssues]);

  const setCellRef = (rowId: string, column: FieldColumnKey, element: HTMLDivElement | null) => {
    const key = cellKey(rowId, column);
    if (element) cellRefs.current.set(key, element);
    else cellRefs.current.delete(key);
  };

  const focusCell = (rowIndex: number, columnIndex: number) => {
    if (!fields.length || !editableColumns.length) return;
    const nextRowIndex = Math.max(0, Math.min(fields.length - 1, rowIndex));
    const nextColumnIndex = Math.max(0, Math.min(editableColumns.length - 1, columnIndex));
    const rowId = fieldRowId(fields[nextRowIndex], nextRowIndex);
    const column = editableColumns[nextColumnIndex].key;
    setActiveCell({ rowId, column });
    requestAnimationFrame(() => {
      cellRefs.current.get(cellKey(rowId, column))?.focus();
    });
  };

  const normalizeSectionField = (field: DimensionFieldRecord) => {
    if (fieldType === "PARTITION") return { ...field, fieldType: "PARTITION" };
    return {
      ...field,
      fieldType: field.fieldType && field.fieldType !== "PARTITION"
        ? field.fieldType
        : "PROPERTY",
    };
  };

  const updateRow = (rowIndex: number, patch: Partial<DimensionFieldRecord>) => {
    onChange(
      fields.map((field, index) =>
        index === rowIndex ? normalizeSectionField({ ...field, ...patch }) : field,
      ),
    );
  };

  const removeRow = (rowIndex: number) => {
    onChange(fields.filter((_field, index) => index !== rowIndex));
    setActiveCell(null);
  };

  const insertRows = () => {
    const count = Math.max(1, Math.min(100, Number(insertCount) || 1));
    const additions = Array.from({ length: count }, () => createDraftField(fieldType));
    const activeIndex = activeCell
      ? fields.findIndex((field, index) => fieldRowId(field, index) === activeCell.rowId)
      : -1;
    const insertAt = activeIndex >= 0 ? activeIndex + 1 : fields.length;
    onChange([...fields.slice(0, insertAt), ...additions, ...fields.slice(insertAt)]);
    setInsertCount(String(count));
    const firstInsertedRowId = fieldRowId(additions[0], insertAt);
    const firstEditableColumn = editableColumns[0]?.key;
    if (firstEditableColumn) {
      setActiveCell({ rowId: firstInsertedRowId, column: firstEditableColumn });
      requestAnimationFrame(() => {
        cellRefs.current.get(cellKey(firstInsertedRowId, firstEditableColumn))?.focus();
      });
    }
  };

  const removeEmptyRows = () => {
    const next = fields.filter((field) => !isEmptyDimensionField(field));
    const removed = fields.length - next.length;
    if (!removed) {
      toast.info("没有可移除的空白行");
      return;
    }
    onChange(next);
    setActiveCell(null);
    toast.success("已移除 " + removed + " 个空白行");
  };

  const applyPaste = (text: string) => {
    if (!activeCell || !editableColumns.length) return false;
    const startRow = fields.findIndex(
      (field, index) => fieldRowId(field, index) === activeCell.rowId,
    );
    const startColumn = editableColumns.findIndex(
      (column) => column.key === activeCell.column,
    );
    if (startRow < 0 || startColumn < 0) return false;

    const matrix = text
      .replace(/\r\n/g, "\n")
      .split("\n")
      .filter((line, index, rows) => line.length > 0 || index < rows.length - 1)
      .map((line) => line.split("\t"));
    if (!matrix.length) return false;

    const next = [...fields];
    const requiredRows = startRow + matrix.length;
    while (next.length < requiredRows) next.push(createDraftField(fieldType));

    matrix.forEach((rowValues, rowOffset) => {
      rowValues.forEach((value, columnOffset) => {
        const targetColumn = editableColumns[startColumn + columnOffset];
        if (!targetColumn) return;
        const targetIndex = startRow + rowOffset;
        next[targetIndex] = normalizeSectionField({
          ...next[targetIndex],
          ...patchFromClipboard(targetColumn.key, value),
        });
      });
    });
    onChange(next);
    return true;
  };

  const handleGridPaste = (event: ClipboardEvent<HTMLDivElement>) => {
    if (!activeCell) return;
    const text = event.clipboardData.getData("text/plain");
    if (!text) return;
    if (applyPaste(text)) event.preventDefault();
  };

  const handleGridKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (!activeCell) return;
    const target = event.target as HTMLElement;
    const interactiveTarget =
      target instanceof HTMLInputElement ||
      target instanceof HTMLTextAreaElement ||
      target.closest('[role="combobox"]') ||
      target.closest("button");

    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "c" && !interactiveTarget) {
      const rowIndex = fields.findIndex(
        (field, index) => fieldRowId(field, index) === activeCell.rowId,
      );
      if (rowIndex >= 0) {
        void navigator.clipboard?.writeText(
          cellClipboardValue(fields[rowIndex], activeCell.column),
        );
      }
      return;
    }

    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "x" && !interactiveTarget) {
      const rowIndex = fields.findIndex(
        (field, index) => fieldRowId(field, index) === activeCell.rowId,
      );
      if (rowIndex >= 0) {
        void navigator.clipboard?.writeText(
          cellClipboardValue(fields[rowIndex], activeCell.column),
        );
        updateRow(rowIndex, patchFromClipboard(activeCell.column, ""));
      }
      return;
    }

    if (event.key === "Delete" && !interactiveTarget) {
      const rowIndex = fields.findIndex(
        (field, index) => fieldRowId(field, index) === activeCell.rowId,
      );
      if (rowIndex >= 0) {
        updateRow(rowIndex, patchFromClipboard(activeCell.column, ""));
        event.preventDefault();
      }
    }
  };

  const handleCellKeyDown = (
    event: KeyboardEvent<HTMLDivElement>,
    rowIndex: number,
    column: FieldColumnKey,
  ) => {
    const columnIndex = editableColumns.findIndex((item) => item.key === column);
    if (columnIndex < 0) return;
    const target = event.target as HTMLElement;
    const isDirectCell = target === event.currentTarget;

    if (event.key === "Tab") {
      event.preventDefault();
      const direction = event.shiftKey ? -1 : 1;
      let nextRow = rowIndex;
      let nextColumn = columnIndex + direction;
      if (nextColumn >= editableColumns.length) {
        nextColumn = 0;
        nextRow += 1;
      } else if (nextColumn < 0) {
        nextColumn = editableColumns.length - 1;
        nextRow -= 1;
      }
      focusCell(nextRow, nextColumn);
      return;
    }

    if (event.key === "Enter") {
      event.preventDefault();
      focusCell(rowIndex + (event.shiftKey ? -1 : 1), columnIndex);
      return;
    }

    if (!isDirectCell) return;
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      focusCell(rowIndex, columnIndex - 1);
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      focusCell(rowIndex, columnIndex + 1);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      focusCell(rowIndex - 1, columnIndex);
    } else if (event.key === "ArrowDown") {
      event.preventDefault();
      focusCell(rowIndex + 1, columnIndex);
    }
  };

  const renderCell = (
    field: DimensionFieldRecord,
    rowIndex: number,
    column: FieldGridColumn,
  ) => {
    const commonInputClass =
      "!h-[27px] !rounded-none !border-0 !bg-transparent !px-2 !text-xs !shadow-none focus-visible:!ring-0";
    switch (column.key) {
      case "sequence":
        return <span className="text-xs text-[var(--yak-components-muted-text)]">{rowIndex + 1}</span>;
      case "fieldName":
        return (
          <Input
            size="small"
            variant="outlined"
            className={commonInputClass}
            value={field.fieldName || ""}
            placeholder="字段名称"
            onChange={(event) => updateRow(rowIndex, { fieldName: event.target.value })}
          />
        );
      case "dataType":
        return (
          <FieldGridSelect
            value={field.dataType || "STRING"}
            options={fieldTypeOptions || []}
            placeholder="类型"
            onChange={(value) => updateRow(rowIndex, { dataType: value })}
          />
        );
      case "cnName":
        return (
          <Input
            size="small"
            variant="outlined"
            className={commonInputClass}
            value={field.cnName || ""}
            placeholder="字段显示名"
            onChange={(event) => updateRow(rowIndex, { cnName: event.target.value })}
          />
        );
      case "length":
        return (
          <Input
            size="small"
            variant="outlined"
            type="number"
            min={0}
            className={commonInputClass}
            value={field.length == null ? "" : String(field.length)}
            placeholder="-"
            onChange={(event) =>
              updateRow(rowIndex, {
                length: event.target.value === "" ? undefined : Number(event.target.value),
              })
            }
          />
        );
      case "fieldPrecision":
        return (
          <Input
            size="small"
            variant="outlined"
            type="number"
            min={0}
            className={commonInputClass}
            value={field.fieldPrecision == null ? "" : String(field.fieldPrecision)}
            placeholder="-"
            onChange={(event) =>
              updateRow(rowIndex, {
                fieldPrecision: event.target.value === "" ? undefined : Number(event.target.value),
              })
            }
          />
        );
      case "fieldScale":
        return (
          <Input size="small" variant="outlined" type="number" min={0}
            className={commonInputClass}
            value={field.fieldScale == null ? "" : String(field.fieldScale)}
            placeholder="小数位"
            onChange={(event) => updateRow(rowIndex, { fieldScale: event.target.value === "" ? undefined : Number(event.target.value) })}
          />
        );
      case "datetimePrecision":
        return (
          <Input size="small" variant="outlined" type="number" min={0}
            className={commonInputClass}
            value={field.datetimePrecision == null ? "" : String(field.datetimePrecision)}
            placeholder="时间精度"
            onChange={(event) => updateRow(rowIndex, { datetimePrecision: event.target.value === "" ? undefined : Number(event.target.value) })}
          />
        );
      case "defaultValue":
        return (
          <Input size="small" variant="outlined" className={commonInputClass}
            value={field.defaultValue || ""} placeholder="默认值"
            onChange={(event) => updateRow(rowIndex, { defaultValue: event.target.value || undefined })}
          />
        );
      case "fieldStdId":
        return (
          <FieldGridSelect
            value={field.fieldStdId || ""}
            options={fieldStandardOptions}
            placeholder="未关联"
            onChange={(value) => updateRow(rowIndex, { fieldStdId: value || undefined })}
          />
        );
      case "codeStdId":
        return (
          <FieldGridSelect
            value={field.codeStdId || ""}
            options={codeStandardOptions}
            placeholder="未关联"
            onChange={(value) => updateRow(rowIndex, { codeStdId: value || undefined })}
          />
        );
      case "unitId":
        return (
          <FieldGridSelect
            value={field.unitId || ""}
            options={unitOptions}
            placeholder="未关联"
            onChange={(value) => updateRow(rowIndex, { unitId: value || undefined })}
          />
        );
      case "primaryFlag":
        return (
          <Checkbox
            checked={Boolean(field.primaryFlag)}
            aria-label="主键"
            onCheckedChange={(checked) => updateRow(rowIndex, { primaryFlag: checked === true })}
          />
        );
      case "notNull":
        return (
          <Checkbox
            checked={Boolean(field.notNull)}
            aria-label="非空"
            onCheckedChange={(checked) => updateRow(rowIndex, { notNull: checked === true })}
          />
        );
      case "sourceTb":
        return (
          <Input
            size="small"
            variant="outlined"
            className={commonInputClass}
            value={field.sourceTb || ""}
            placeholder="来源表"
            onChange={(event) => updateRow(rowIndex, { sourceTb: event.target.value || undefined })}
          />
        );
      case "sourceField":
        return (
          <Input
            size="small"
            variant="outlined"
            className={commonInputClass}
            value={field.sourceField || ""}
            placeholder="来源字段"
            onChange={(event) => updateRow(rowIndex, { sourceField: event.target.value || undefined })}
          />
        );
      case "fieldType":
        return (
          <FieldGridSelect
            value={field.fieldType && field.fieldType !== "PARTITION" ? field.fieldType : "PROPERTY"}
            options={FIELD_CATEGORY_OPTIONS}
            placeholder="字段类别"
            onChange={(value) => updateRow(rowIndex, { fieldType: value || "PROPERTY" })}
          />
        );
      case "remark":
        return (
          <Input
            size="small"
            variant="outlined"
            className={commonInputClass}
            value={field.remark || ""}
            placeholder="描述"
            onChange={(event) => updateRow(rowIndex, { remark: event.target.value || undefined })}
          />
        );
      case "actions":
        return (
          <Button
            size="sm"
            variant="ghost"
            isIconOnly
            title="删除字段"
            aria-label="删除字段"
            className="!size-6 !min-w-6 text-[var(--yak-components-danger-text)]"
            onClick={() => removeRow(rowIndex)}
          >
            <Trash2 size={12} />
          </Button>
        );
    }
  };

  const spreadsheetTitle = fieldType === "PARTITION" ? "分区字段" : "字段";

  return (
    <>
      <div className="space-y-2">
        <FieldSpreadsheetToolbar
          insertCount={insertCount}
          validationIssueCount={validationIssues.length}
          availableColumns={availableColumns}
          visibleColumns={visibleColumns}
          onInsertCountChange={setInsertCount}
          onInsertRows={insertRows}
          onRemoveEmptyRows={removeEmptyRows}
          onVisibleColumnsChange={setVisibleColumns}
        />

        <div
          role="grid"
          aria-label={spreadsheetTitle + "电子表格"}
          className="min-w-0 overflow-x-auto rounded border border-[var(--yak-components-panel-border)] bg-white"
          onPaste={handleGridPaste}
          onKeyDown={handleGridKeyDown}
        >
          <div
            role="row"
            className="grid w-full bg-[var(--yak-color-hover)]"
            style={{
              minWidth: minGridWidth,
              gridTemplateColumns: templateColumns,
            }}
          >
            {columns.map((column) => (
              <div
                key={column.key}
                role="columnheader"
                className="flex h-7 items-center justify-center border-b border-r border-[var(--yak-components-panel-border)] px-2 text-xs font-medium text-[var(--yak-components-panel-text)] last:border-r-0"
              >
                {column.title}
              </div>
            ))}
          </div>

          {loading ? (
            <div className="flex min-h-20 items-center justify-center text-xs text-[var(--yak-components-muted-text)]">
              正在加载{spreadsheetTitle}...
            </div>
          ) : error ? (
            <div className="flex min-h-20 items-center justify-center text-xs text-[var(--yak-components-danger-text)]">
              {spreadsheetTitle}加载失败，请刷新后重试
            </div>
          ) : fields.length === 0 ? (
            <div className="flex min-h-20 items-center justify-center text-xs text-[var(--yak-components-muted-text)]">
              暂无{spreadsheetTitle}，可插入行后直接编辑
            </div>
          ) : (
            fields.map((field, rowIndex) => {
              const rowId = fieldRowId(field, rowIndex);
              return (
                <div
                  key={rowId}
                  role="row"
                  className="grid w-full"
                  style={{
                    minWidth: minGridWidth,
                    gridTemplateColumns: templateColumns,
                  }}
                >
                  {columns.map((column) => {
                    const active =
                      activeCell?.rowId === rowId && activeCell.column === column.key;
                    const issue = issuesByCell.get(cellKey(rowId, column.key));
                    const editable = EDITABLE_FIELD_COLUMNS.includes(column.key);
                    return (
                      <div
                        key={column.key}
                        ref={editable ? (element) => setCellRef(rowId, column.key, element) : undefined}
                        role="gridcell"
                        tabIndex={editable ? 0 : -1}
                        title={issue || undefined}
                        className={[
                          "relative flex h-7 min-w-0 items-center border-b border-r border-[var(--yak-components-panel-border)] bg-white outline-none last:border-r-0",
                          column.key === "sequence" || column.key === "primaryFlag" || column.key === "notNull" || column.key === "actions"
                            ? "justify-center"
                            : "",
                          active
                            ? "z-[1] ring-2 ring-inset ring-[var(--yak-color-primary)]"
                            : "",
                          issue
                            ? "ring-1 ring-inset ring-[var(--yak-components-danger-text)]"
                            : "",
                        ].join(" ")}
                        onFocus={() => {
                          if (editable) setActiveCell({ rowId, column: column.key });
                        }}
                        onPointerDown={() => {
                          if (editable) setActiveCell({ rowId, column: column.key });
                        }}
                        onKeyDown={(event) => {
                          if (editable) handleCellKeyDown(event, rowIndex, column.key);
                        }}
                      >
                        {renderCell(field, rowIndex, column)}
                      </div>
                    );
                  })}
                </div>
              );
            })
          )}
        </div>
      </div>

    </>
  );
}

export default FieldSpreadsheet;
