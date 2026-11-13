import type { ModelingRecord } from "../../service";

export interface DataMartRecord extends ModelingRecord {
  id?: string;
  enShortName?: string;
  martName?: string;
  martType?: string | { code?: string; name?: string };
  martTypeName?: string;
  businessTypeId?: string;
  businessTypeName?: string;
  director?: string;
  remark?: string;
  parentId?: string | null;
  createBy?: string;
  createTime?: string;
  updateBy?: string;
  updateTime?: string;
}

export interface DataMartTreeNode {
  key: string;
  value: string;
  title: string;
  parentId?: string | null;
  children?: DataMartTreeNode[];
}

export interface DataMartFormValues {
  enShortName: string;
  martName: string;
  martType: string;
  businessTypeId: string;
  director: string;
  remark: string;
}

export const EMPTY_DATA_MART_FORM: DataMartFormValues = {
  enShortName: "",
  martName: "",
  martType: "BUSINESS",
  businessTypeId: "",
  director: "",
  remark: "",
};

export const MART_TYPE_OPTIONS = [
  { value: "PUBLIC", label: "公共集市" },
  { value: "BUSINESS", label: "业务集市" },
  { value: "APPLICATION", label: "数据应用集市" },
];

export function getMartTypeLabel(record?: DataMartRecord): string {
  if (!record) return "-";
  if (record.martTypeName) return record.martTypeName;
  const value = typeof record.martType === "string"
    ? record.martType
    : record.martType?.name || record.martType?.code;
  return MART_TYPE_OPTIONS.find((item) => item.value === value)?.label || value || "-";
}
