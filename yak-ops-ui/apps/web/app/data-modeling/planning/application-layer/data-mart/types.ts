import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import { MART_TYPE_OPTIONS } from "../../select-options";

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

export interface DataMartOption extends ModelingRecord {
  id?: string;
  martName?: string;
}

export interface BusinessTypeOption extends ModelingRecord {
  id?: string;
  businessName?: string;
}

export function getMartTypeLabel(record?: DataMartRecord): string {
  if (!record) return "-";
  if (record.martTypeName) return record.martTypeName;
  const value = typeof record.martType === "string"
    ? record.martType
    : record.martType?.name || record.martType?.code;
  return MART_TYPE_OPTIONS.find((item) => item.value === value)?.label || value || "-";
}
