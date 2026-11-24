import type { DimensionFieldRecord } from "@/service/data-modeling/models/dimension-modeling";

export type FieldKind = "PROPERTY" | "PARTITION";

export type FieldOption = {
  value: string;
  label: string;
};

export type FieldColumnKey =
  | "sequence"
  | "fieldName"
  | "dataType"
  | "cnName"
  | "length"
  | "fieldPrecision"
  | "fieldStdId"
  | "codeStdId"
  | "unitId"
  | "primaryFlag"
  | "notNull"
  | "sourceTb"
  | "sourceField"
  | "fieldType"
  | "remark"
  | "actions";

export type FieldGridColumn = {
  key: FieldColumnKey;
  title: string;
  minWidth: number;
  width?: number;
  flex?: number;
  configurable?: boolean;
  defaultVisible?: boolean;
};

export type FieldValidationIssue = {
  rowId: string;
  column: FieldColumnKey;
  message: string;
};

export type FieldSpreadsheetProps = {
  modelType?: string;
  fieldType: FieldKind;
  fields: DimensionFieldRecord[];
  fieldStandardOptions: FieldOption[];
  codeStandardOptions: FieldOption[];
  unitOptions: FieldOption[];\n  fieldTypeOptions?: FieldOption[];
  loading?: boolean;
  error?: boolean;
  onChange: (fields: DimensionFieldRecord[]) => void;
};

export const FIELD_GRID_COLUMNS: FieldGridColumn[] = [
  { key: "sequence", title: "序号", minWidth: 44, width: 44, defaultVisible: true },
  { key: "fieldName", title: "字段名称", minWidth: 180, flex: 1.3, defaultVisible: true },
  { key: "dataType", title: "类型", minWidth: 120, flex: 0.8, defaultVisible: true },
  { key: "cnName", title: "字段显示名", minWidth: 180, flex: 1.3, defaultVisible: true },
  { key: "length", title: "长度", minWidth: 88, flex: 0.55, configurable: true },
  { key: "fieldPrecision", title: "精度", minWidth: 88, flex: 0.55, configurable: true },\n  { key: "fieldScale", title: "小数位", minWidth: 76, flex: 0.5, configurable: true },\n  { key: "datetimePrecision", title: "时间精度", minWidth: 88, flex: 0.55, configurable: true },\n  { key: "defaultValue", title: "默认值", minWidth: 120, flex: 0.8, configurable: true },
  { key: "fieldStdId", title: "关联字段标准", minWidth: 160, flex: 1, configurable: true },
  { key: "codeStdId", title: "关联标准代码", minWidth: 160, flex: 1, configurable: true },
  { key: "unitId", title: "度量单位", minWidth: 120, flex: 0.8, configurable: true },
  { key: "primaryFlag", title: "主键", minWidth: 64, width: 64, defaultVisible: true },
  { key: "notNull", title: "非空", minWidth: 64, width: 64, defaultVisible: true },
  { key: "sourceTb", title: "来源表", minWidth: 180, flex: 1.2, configurable: true },
  { key: "sourceField", title: "来源字段", minWidth: 150, flex: 1, configurable: true },
  { key: "fieldType", title: "字段类别", minWidth: 110, flex: 0.75, configurable: true },
  { key: "remark", title: "描述", minWidth: 200, flex: 1.5, configurable: true },
  { key: "actions", title: "操作", minWidth: 80, width: 80, defaultVisible: true },
];

export const DEFAULT_VISIBLE_FIELD_COLUMNS = new Set<FieldColumnKey>(
  FIELD_GRID_COLUMNS.filter((column) => column.defaultVisible).map((column) => column.key),
);

const SOURCE_PROPERTY_COLUMN_KEYS: FieldColumnKey[] = [
  "sequence",
  "fieldName",
  "dataType",
  "cnName",
  "remark",
  "primaryFlag",
  "notNull",
  "sourceTb",
  "sourceField",
  "fieldType",
  "fieldStdId",
  "codeStdId",
  "actions",
];

const SOURCE_PARTITION_COLUMN_KEYS: FieldColumnKey[] = [
  "sequence",
  "fieldName",
  "dataType",
  "cnName",
  "remark",
  "primaryFlag",
  "notNull",
  "sourceTb",
  "sourceField",
  "fieldStdId",
  "codeStdId",
  "actions",
];

export function fieldGridColumns(modelType?: string, fieldKind: FieldKind = "PROPERTY") {
  if (modelType !== "SOURCE") return FIELD_GRID_COLUMNS;
  const order = fieldKind === "PARTITION"
    ? SOURCE_PARTITION_COLUMN_KEYS
    : SOURCE_PROPERTY_COLUMN_KEYS;
  const byKey = new Map(FIELD_GRID_COLUMNS.map((column) => [column.key, column]));
  return order
    .map((key) => byKey.get(key))
    .filter((column): column is FieldGridColumn => Boolean(column));
}

export function defaultVisibleFieldColumns(modelType?: string, fieldKind: FieldKind = "PROPERTY") {
  return new Set<FieldColumnKey>(
    fieldGridColumns(modelType, fieldKind)
      .filter((column) => column.defaultVisible)
      .map((column) => column.key),
  );
}

export const DATA_TYPE_OPTIONS: FieldOption[] = [
  "INT",
  "BIGINT",
  "BOOLEAN",
  "STRING",
  "TEXT",
  "DECIMAL",
  "DATE",
  "DATETIME",
  "TIMESTAMP",
  "JSON",
  "ARRAY",
  "DOUBLE",
  "MAP",
  "STRUCT",
].map((value) => ({ value, label: value }));


export const FIELD_CATEGORY_OPTIONS: FieldOption[] = [
  { value: "PROPERTY", label: "属性" },
  { value: "DIMENSION", label: "维度" },
  { value: "MEASUREMENT", label: "度量" },
];
