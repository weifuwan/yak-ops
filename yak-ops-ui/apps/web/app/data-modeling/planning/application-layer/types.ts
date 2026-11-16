import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import { MART_TYPE_OPTIONS } from "../select-options";

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

export function getMartTypeLabel(record?: DataMartRecord): string {
  if (!record) return "-";
  if (record.martTypeName) return record.martTypeName;
  const value = typeof record.martType === "string"
    ? record.martType
    : record.martType?.name || record.martType?.code;
  return MART_TYPE_OPTIONS.find((item) => item.value === value)?.label || value || "-";
}

export interface SubjectDomainRecord extends ModelingRecord {
  id?: string;
  enShortName?: string;
  domainName?: string;
  director?: string;
  dataMartId?: string;
  dataMartName?: string;
  parentId?: string | null;
  remark?: string;
  createBy?: string;
  createTime?: string;
  updateBy?: string;
  updateTime?: string;
}

export interface SubjectDomainTreeNode {
  key: string;
  value: string;
  title: string;
  parentId?: string | null;
  type: "MART" | "SUBJECT";
  children?: SubjectDomainTreeNode[];
}

export interface DataMartOption extends ModelingRecord {
  id?: string;
  martName?: string;
}

export interface SubjectDomainFormValues {
  enShortName: string;
  domainName: string;
  director: string;
  dataMartId: string;
  remark: string;
}

export const EMPTY_SUBJECT_DOMAIN_FORM: SubjectDomainFormValues = {
  enShortName: "",
  domainName: "",
  director: "",
  dataMartId: "",
  remark: "",
};

export function getSubjectDomainId(node: SubjectDomainTreeNode): string {
  const value = node.value || node.key;
  const suffixIndex = value.lastIndexOf("-");
  return suffixIndex > 0 ? value.slice(0, suffixIndex) : value;
}
