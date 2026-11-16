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
