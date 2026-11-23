import type { DimensionFieldRecord } from "@/service/data-modeling/models/dimension-modeling";
import type { FieldColumnKey, FieldKind, FieldValidationIssue } from "./field-grid-types";

const DEFAULT_DATA_TYPE = "STRING";

export function fieldRowId(field: DimensionFieldRecord, index: number) {
  return String(field.id || field.tempId || "field-" + index);
}

export function createDraftField(fieldType: FieldKind): DimensionFieldRecord {
  return {
    tempId:
      "draft-" +
      Date.now().toString(36) +
      "-" +
      Math.random().toString(36).slice(2, 8),
    fieldName: "",
    cnName: "",
    dataType: DEFAULT_DATA_TYPE,
    fieldType,
    primaryFlag: false,
    notNull: false,
  };
}

export function isEmptyDimensionField(field: DimensionFieldRecord) {
  return (
    !field.fieldName?.trim() &&
    !field.cnName?.trim() &&
    !field.remark?.trim() &&
    !field.fieldStdId &&
    !field.codeStdId &&
    !field.unitId &&
    !field.sourceTb?.trim() &&
    !field.sourceField?.trim() &&
    !field.primaryFlag &&
    !field.notNull &&
    field.length == null &&
    field.precision == null
  );
}

export function fieldsForSave(fields: DimensionFieldRecord[]) {
  return fields.filter((field) => !isEmptyDimensionField(field));
}

export function validateDimensionFields(fields: DimensionFieldRecord[]): FieldValidationIssue[] {
  const issues: FieldValidationIssue[] = [];
  const active = fields
    .map((field, index) => ({ field, index, rowId: fieldRowId(field, index) }))
    .filter(({ field }) => !isEmptyDimensionField(field));

  const nameRows = new Map<string, string[]>();
  for (const { field, rowId } of active) {
    const fieldName = field.fieldName?.trim() || "";
    if (!fieldName) {
      issues.push({ rowId, column: "fieldName", message: "字段名称不能为空" });
    } else {
      const normalized = fieldName.toLowerCase();
      nameRows.set(normalized, [...(nameRows.get(normalized) || []), rowId]);
    }

    if (!field.dataType?.trim()) {
      issues.push({ rowId, column: "dataType", message: "字段类型不能为空" });
    }
    if (field.length != null && Number(field.length) < 0) {
      issues.push({ rowId, column: "length", message: "长度不能小于 0" });
    }
    if (field.precision != null && Number(field.precision) < 0) {
      issues.push({ rowId, column: "precision", message: "精度不能小于 0" });
    }
    if (
      field.length != null &&
      field.precision != null &&
      Number(field.precision) > Number(field.length)
    ) {
      issues.push({ rowId, column: "precision", message: "精度不能大于长度" });
    }
  }

  for (const rowIds of nameRows.values()) {
    if (rowIds.length <= 1) continue;
    rowIds.forEach((rowId) => {
      issues.push({ rowId, column: "fieldName", message: "字段名称重复" });
    });
  }

  return issues;
}

export function normalizeImportedDataType(typeName?: string) {
  const raw = String(typeName || DEFAULT_DATA_TYPE).trim().toUpperCase();
  if (!raw) return DEFAULT_DATA_TYPE;
  if (/^(VARCHAR|VARCHAR2|CHAR|NCHAR|NVARCHAR|NVARCHAR2|CHARACTER)/.test(raw)) return "STRING";
  if (/^(TINYINT|SMALLINT|MEDIUMINT|INTEGER)$/.test(raw)) return "INT";
  if (/^(LONG|INT8|BIGSERIAL)/.test(raw)) return "BIGINT";
  if (/^(BOOL)/.test(raw)) return "BOOLEAN";
  if (/^(NUMERIC|NUMBER)/.test(raw)) return "DECIMAL";
  if (/^(FLOAT|REAL|DOUBLE)/.test(raw)) return "DOUBLE";
  if (/^TIMESTAMP/.test(raw)) return "TIMESTAMP";
  if (/^(DATETIME)/.test(raw)) return "DATETIME";
  if (/^DATE$/.test(raw)) return "DATE";
  if (/^(JSON|JSONB)/.test(raw)) return "JSON";
  if (/^(TEXT|CLOB|LONGTEXT|MEDIUMTEXT)/.test(raw)) return "TEXT";
  if (raw.endsWith("[]")) return "ARRAY";
  return raw.split(/[ (]/)[0] || DEFAULT_DATA_TYPE;
}

export function normalizeFieldCategory(value?: string) {
  const normalized = String(value || "").trim().toUpperCase();
  if (!normalized || normalized === "属性") return "PROPERTY";
  if (normalized === "维度") return "DIMENSION";
  if (normalized === "度量") return "MEASUREMENT";
  if (["PROPERTY", "DIMENSION", "MEASUREMENT"].includes(normalized)) return normalized;
  return "PROPERTY";
}

function parseBooleanCell(value?: string) {
  const normalized = String(value || "").trim().toLowerCase();
  return ["1", "true", "yes", "y", "是", "√", "checked"].includes(normalized);
}

function parseOptionalNumber(value?: string) {
  const normalized = String(value || "").trim();
  if (!normalized) return undefined;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function cellClipboardValue(field: DimensionFieldRecord, column: FieldColumnKey) {
  switch (column) {
    case "fieldName":
      return field.fieldName || "";
    case "dataType":
      return field.dataType || "";
    case "cnName":
      return field.cnName || "";
    case "length":
      return field.length == null ? "" : String(field.length);
    case "precision":
      return field.precision == null ? "" : String(field.precision);
    case "fieldStdId":
      return field.fieldStdId || "";
    case "codeStdId":
      return field.codeStdId || "";
    case "unitId":
      return field.unitId || "";
    case "primaryFlag":
      return field.primaryFlag ? "true" : "false";
    case "notNull":
      return field.notNull ? "true" : "false";
    case "sourceTb":
      return field.sourceTb || "";
    case "sourceField":
      return field.sourceField || "";
    case "fieldType":
      return field.fieldType || "PROPERTY";
    case "remark":
      return field.remark || "";
    default:
      return "";
  }
}

export function patchFromClipboard(
  column: FieldColumnKey,
  value: string,
): Partial<DimensionFieldRecord> {
  switch (column) {
    case "fieldName":
      return { fieldName: value.trim() };
    case "dataType":
      return { dataType: normalizeImportedDataType(value) };
    case "cnName":
      return { cnName: value.trim() };
    case "length":
      return { length: parseOptionalNumber(value) };
    case "precision":
      return { precision: parseOptionalNumber(value) };
    case "fieldStdId":
      return { fieldStdId: value.trim() || undefined };
    case "codeStdId":
      return { codeStdId: value.trim() || undefined };
    case "unitId":
      return { unitId: value.trim() || undefined };
    case "primaryFlag":
      return { primaryFlag: parseBooleanCell(value) };
    case "notNull":
      return { notNull: parseBooleanCell(value) };
    case "sourceTb":
      return { sourceTb: value.trim() || undefined };
    case "sourceField":
      return { sourceField: value.trim() || undefined };
    case "fieldType":
      return { fieldType: normalizeFieldCategory(value) };
    case "remark":
      return { remark: value.trim() || undefined };
    default:
      return {};
  }
}

export const EDITABLE_FIELD_COLUMNS: FieldColumnKey[] = [
  "fieldName",
  "dataType",
  "cnName",
  "length",
  "precision",
  "fieldStdId",
  "codeStdId",
  "unitId",
  "primaryFlag",
  "notNull",
  "sourceTb",
  "sourceField",
  "fieldType",
  "remark",
];
