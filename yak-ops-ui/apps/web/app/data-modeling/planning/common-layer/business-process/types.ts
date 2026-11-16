import type { BusinessProcessRecord } from "../data-domain/types";

export interface BusinessProcessFilters {
  enShortName: string;
  enName: string;
  cnName: string;
  dataDomainId: string;
  createBy: string;
  director: string;
}

export interface BusinessProcessFormValues {
  enShortName: string;
  enName: string;
  cnName: string;
  dataDomainId: string;
  director: string;
  remark: string;
}

export const EMPTY_FILTERS: BusinessProcessFilters = {
  enShortName: "",
  enName: "",
  cnName: "",
  dataDomainId: "",
  createBy: "",
  director: "",
};

export const EMPTY_FORM: BusinessProcessFormValues = {
  enShortName: "",
  enName: "",
  cnName: "",
  dataDomainId: "",
  director: "",
  remark: "",
};

export type { BusinessProcessRecord };
