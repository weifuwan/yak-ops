import type { ModelingRecord } from "../../service";

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
