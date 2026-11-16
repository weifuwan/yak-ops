import type { ModelingRecord } from "@/service/data-modeling/shared/types";

export interface BusinessTypeRecord extends ModelingRecord {
  id?: string;
  businessName?: string;
  enShortName?: string;
  reviewer?: string;
  director?: string;
  parentId?: string;
  remark?: string;
  createBy?: string;
  createTime?: string;
  updateBy?: string;
  updateTime?: string;
}

export interface DataMartRecord extends ModelingRecord {
  id?: string;
  martName?: string;
  enShortName?: string;
  martType?: string | { code?: string; name?: string };
  martTypeName?: string;
  businessTypeId?: string;
  director?: string;
  remark?: string;
  createTime?: string;
}

export interface BusinessTypeFormValues {
  businessName: string;
  enShortName: string;
  reviewer: string;
  director: string;
  remark: string;
}

export const EMPTY_FORM: BusinessTypeFormValues = {
  businessName: "",
  enShortName: "",
  reviewer: "",
  director: "",
  remark: "",
};
