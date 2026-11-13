import type { ModelingRecord } from "../../service";

export interface DwLayerRecord extends ModelingRecord {
  id?: string;
  enShortName?: string;
  enName?: string;
  cnName?: string;
  dwType?: string | { code?: string; value?: string; name?: string };
  dwTypeName?: string;
  dwModelType?: string | { code?: string; value?: string; name?: string };
  director?: string;
  remark?: string;
  createBy?: string;
  createTime?: string;
  updateBy?: string;
  updateTime?: string;
}

export interface DwLayerFormValues {
  enShortName: string;
  enName: string;
  cnName: string;
  dwType: string;
  dwModelType: string;
  director: string;
  remark: string;
}

export interface DwLayerTypeOption {
  value: string;
  label: string;
  modelKeys: string[];
}

export interface DwModelTypeOption {
  value: string;
  label: string;
}

export const DW_LAYER_TYPES: DwLayerTypeOption[] = [
  { value: "SOURCE_LAYER", label: "贴源层", modelKeys: ["SOURCE"] },
  {
    value: "PUBLIC_LAYER",
    label: "公共层",
    modelKeys: ["DIMENSION", "DIM", "DIMENSION_DIM", "DETAIL", "SUMMARY"],
  },
  {
    value: "APPLICATION_LAYER",
    label: "应用层",
    modelKeys: ["APPLICATION", "DIMENSION", "DIM", "DIMENSION_DIM"],
  },
  { value: "NONE_LAYER", label: "未分层", modelKeys: [] },
];

export const DW_MODEL_TYPES: DwModelTypeOption[] = [
  { value: "SOURCE", label: "贴源表" },
  { value: "APPLICATION", label: "应用表" },
  { value: "DIMENSION", label: "维度表" },
  { value: "DIM", label: "维度" },
  { value: "DIMENSION_DIM", label: "维度表 / 维度" },
  { value: "DETAIL", label: "明细表" },
  { value: "SUMMARY", label: "汇总表" },
];

export function getEnumCode(value: unknown): string {
  if (typeof value === "string") return value;
  if (value && typeof value === "object") {
    const item = value as Record<string, unknown>;
    const code = item.code ?? item.value ?? item.name;
    return typeof code === "string" ? code : "";
  }
  return "";
}

export function getDwModelTypeOptions(dwType: string): DwModelTypeOption[] {
  const type = DW_LAYER_TYPES.find((item) => item.value === dwType);
  if (!type) return [];
  return DW_MODEL_TYPES.filter((item) => type.modelKeys.includes(item.value));
}

export function getDwTypeLabel(value: unknown, fallback?: string): string {
  const code = getEnumCode(value);
  return fallback || DW_LAYER_TYPES.find((item) => item.value === code)?.label || "其他";
}

export function getDwModelTypeLabel(value: unknown): string {
  const code = getEnumCode(value);
  return DW_MODEL_TYPES.find((item) => item.value === code)?.label || (code ? code : "-");
}

export function toDwLayerFormValues(record?: DwLayerRecord): DwLayerFormValues {
  return {
    enShortName: record?.enShortName || "",
    enName: record?.enName || "",
    cnName: record?.cnName || "",
    dwType: getEnumCode(record?.dwType),
    dwModelType: getEnumCode(record?.dwModelType),
    director: record?.director || "",
    remark: record?.remark || "",
  };
}
