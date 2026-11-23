import { useMemo, useRef, useState } from "react";
import type { ClipboardEvent, KeyboardEvent } from "react";
import { Button, Checkbox, Input, toast } from "@yak-ops/yak-ui";
import { Trash2 } from "lucide-react";

import type { DimensionFieldRecord } from "@/service/data-modeling/models/dimension-modeling";
import FieldBatchImportDialog from "./field-batch-import-dialog";
import FieldGridSelect from "./field-grid-select";
import FieldReferenceDialog from "./field-reference-dialog";
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
  DATA_TYPE_OPTIONS,
  DEFAULT_VISIBLE_FIELD_COLUMNS,
  FIELD_GRID_COLUMNS,
  type FieldColumnKey,
  type FieldGridColumn,
  type FieldSpreadsheetProps,
} from "./field-grid-types";

type ActiveCell = {
  rowId: string;
  column: FieldColumnKey;
};

function gridTemplate(columns: FieldGridColumn[]) {
  return columns.map((column) => column.width + "px").join(" ");
}

function cellKey(rowId: string, column: FieldColumnKey) {
  return rowId + "::" + column;
}

function inputValue(value: number | undefined) {
  return value == null ? "" : String(value);
}

export function FieldSpreadsheet({
  projectCode,
  fieldType,
  fields,
  fieldStandardOptions,
  codeStandardOptions,
  unitOptions,
  loading = false,
  error = false,
  onChange,
}: FieldSpreadsheetProps) {
  const [visibleColumns, setVisibleColumns] = useState<Set<FieldColumnKey>>(
    () => new Set(DEFAULT_VISIBLE_FIELD_COLUMNS),
  );
  const [insertCount, setInsertCount] = useState("1");
  const [activeCell, setActiveCell] = useState<ActiveCell | null>(null);
  const [referenceOpen, setReferenceOpen] = useState(false);
  const [batchImportOpen, setBatchImportOpen] = useState(false);
  const cellRefs = useRef(new Map<string, HTMLDivElement>());

  const columns = useMemo(
    () =>
      FIELD_GRID_COLUMNS.filter(
        (column) => !column.configurable || visibleColumns.has(column.key),
      ),
    [visibleColumns],
  );
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

  const updateRow = (rowIndex: number, patch: Partial<DimensionFieldRecord>) => {
    onChange(
      fields.map((field, index) =>
        index === rowIndex ? { ...field, ...patch, fieldType } : field,
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

  const appendImportedFields = (incoming: DimensionFieldRecord[]) => {
    const names = new Set(
      fields
        .map((field) => field.fieldName?.trim().toLowerCase())
        .filter((name): name is string => Boolean(name)),
    );
    const accepted: DimensionFieldRecord[] = [];
    let skipped = 0;
    incoming.forEach((field) => {
      const name = field.fieldName?.trim().toLowerCase();
      if (name && names.has(name)) {
        skipped += 1;
        return;
      }
      if (name) names.add(name);
      accepted.push({ ...field, fieldType });
    });
    if (accepted.length) onChange([...fields, ...accepted]);
    if (skipped) toast.warning("已跳过 " + skipped + " 个同名字段");
    if (accepted.length) toast.success("已导入 " + accepted.length + " 个字段");
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
        next[targetIndex] = {
          ...next[targetIndex],
          ...patchFromClipboard(targetColumn.key, value),
          fieldType,
        };
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
            options={DATA_TYPE_OPTIONS}
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
            value={inputValue(field.length)}
            placeholder="-"
            onChange={(event) =>
              updateRow(rowIndex, {
                length: event.target.value === "" ? undefined : Number(event.target.value),
              })
            }
          />
        );
      case "precision":
        return (
          <Input
            size="small"
            variant="outlined"
            type="number"
            min={0}
            className={commonInputClass}
            value={inputValue(field.precision)}
            placeholder="-"
            onChange={(event) =>
              updateRow(rowIndex, {
                precision: event.target.value === "" ? undefined : Number(event.target.value),
              })
            }
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
            className="!h-6 !px-1 text-xs text-[var(--yak-components-danger-text)]"
            onClick={() => removeRow(rowIndex)}
          >
            <Trash2 size={12} />
            删除
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
          visibleColumns={visibleColumns}
          onInsertCountChange={setInsertCount}
          onInsertRows={insertRows}
          onRemoveEmptyRows={removeEmptyRows}
          onOpenReference={() => setReferenceOpen(true)}
          onOpenBatchImport={() => setBatchImportOpen(true)}
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
            className="grid w-max min-w-full bg-[var(--yak-color-hover)]"
            style={{ gridTemplateColumns: gridTemplate(columns) }}
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
              暂无{spreadsheetTitle}，可插入行、引用已有表或批量导入
            </div>
          ) : (
            fields.map((field, rowIndex) => {
              const rowId = fieldRowId(field, rowIndex);
              return (
                <div
                  key={rowId}
                  role="row"
                  className="grid w-max min-w-full"
                  style={{ gridTemplateColumns: gridTemplate(columns) }}
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

      <FieldReferenceDialog
        open={referenceOpen}
        projectCode={projectCode}
        fieldType={fieldType}
        onClose={() => setReferenceOpen(false)}
        onImport={appendImportedFields}
      />
      <FieldBatchImportDialog
        open={batchImportOpen}
        fieldType={fieldType}
        onClose={() => setBatchImportOpen(false)}
        onImport={appendImportedFields}
      />
    </>
  );
}

export default FieldSpreadsheet;
