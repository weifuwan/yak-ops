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
  | "precision"
  | "fieldStdId"
  | "codeStdId"
  | "unitId"
  | "primaryFlag"
  | "notNull"
  | "sourceTb"
  | "sourceField"
  | "remark"
  | "actions";

export type FieldGridColumn = {
  key: FieldColumnKey;
  title: string;
  width: number;
  configurable?: boolean;
  defaultVisible?: boolean;
};

export type FieldValidationIssue = {
  rowId: string;
  column: FieldColumnKey;
  message: string;
};

export type FieldSpreadsheetProps = {
  projectCode: string;
  fieldType: FieldKind;
  fields: DimensionFieldRecord[];
  fieldStandardOptions: FieldOption[];
  codeStandardOptions: FieldOption[];
  unitOptions: FieldOption[];
  loading?: boolean;
  error?: boolean;
  onChange: (fields: DimensionFieldRecord[]) => void;
};

export const FIELD_GRID_COLUMNS: FieldGridColumn[] = [
  { key: "sequence", title: "序号", width: 44, defaultVisible: true },
  { key: "fieldName", title: "字段名称", width: 220, defaultVisible: true },
  { key: "dataType", title: "类型", width: 150, defaultVisible: true },
  { key: "cnName", title: "字段显示名", width: 220, defaultVisible: true },
  { key: "length", title: "长度", width: 96, configurable: true },
  { key: "precision", title: "精度", width: 96, configurable: true },
  { key: "fieldStdId", title: "字段标准", width: 180, configurable: true },
  { key: "codeStdId", title: "标准代码", width: 180, configurable: true },
  { key: "unitId", title: "度量单位", width: 150, configurable: true },
  { key: "primaryFlag", title: "主键", width: 64, defaultVisible: true },
  { key: "notNull", title: "非空", width: 64, defaultVisible: true },
  { key: "sourceTb", title: "来源表", width: 220, configurable: true },
  { key: "sourceField", title: "来源字段", width: 160, configurable: true },
  { key: "remark", title: "描述", width: 220, configurable: true },
  { key: "actions", title: "操作", width: 72, defaultVisible: true },
];

export const DEFAULT_VISIBLE_FIELD_COLUMNS = new Set<FieldColumnKey>(
  FIELD_GRID_COLUMNS.filter((column) => column.defaultVisible).map((column) => column.key),
);

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
