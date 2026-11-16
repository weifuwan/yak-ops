import type { ModelingRecord } from "@/service/data-modeling/shared/types";

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

export const isSystemRecord = (record?: { flag?: number | string }) =>
  Number(record?.flag) === 1;

export const isSystemBusinessProcess = (record?: { flag?: number | string; enShortName?: string }) =>
  Number(record?.flag) === 1 || String(record?.enShortName || "").toLocaleLowerCase().endsWith("_default");
