import type { ModelingRecord } from "../../service";

export interface DataDomainRecord extends ModelingRecord {
  id?: string;
  enShortName?: string;
  enName?: string;
  cnName?: string;
  reviewer?: string;
  director?: string;
  remark?: string;
  flag?: number | string;
  createBy?: string;
  createTime?: string;
  updateBy?: string;
  updateTime?: string;
}

export interface BusinessProcessRecord extends ModelingRecord {
  id?: string;
  enShortName?: string;
  enName?: string;
  cnName?: string;
  dataDomainId?: string;
  dataDomainCnName?: string;
  director?: string;
  remark?: string;
  flag?: number | string;
  createBy?: string;
  createTime?: string;
  updateBy?: string;
  updateTime?: string;
}

export interface DataDomainFormValues {
  enShortName: string;
  enName: string;
  cnName: string;
  reviewer: string;
  director: string;
  remark: string;
}

export interface BusinessProcessFormValues {
  enShortName: string;
  enName: string;
  cnName: string;
  director: string;
  remark: string;
}

export const EMPTY_DATA_DOMAIN_FORM: DataDomainFormValues = {
  enShortName: "",
  enName: "",
  cnName: "",
  reviewer: "",
  director: "",
  remark: "",
};

export const EMPTY_BUSINESS_PROCESS_FORM: BusinessProcessFormValues = {
  enShortName: "",
  enName: "",
  cnName: "",
  director: "",
  remark: "",
};

export const isSystemRecord = (record?: { flag?: number | string }) => Number(record?.flag) === 1;
