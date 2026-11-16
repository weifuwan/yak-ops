import type { IndicatorFieldOption, IndicatorOptionResource } from "../types";

export const DW_TYPE_OPTIONS: IndicatorFieldOption[] = [
  { value: "SOURCE_LAYER", label: "贴源层" },
  { value: "PUBLIC_LAYER", label: "公共层" },
  { value: "APPLICATION_LAYER", label: "应用层" },
  { value: "NONE_LAYER", label: "未分层" },
];

export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

export const RELATION_FIELDS: Record<string, IndicatorOptionResource> = {
  atomicId: "atomIndicator",
  timePeriodId: "timePeriod",
  deriveId: "derivativeIndicator",
  categoryId: "timePeriodCategory",
  unitId: "unitStandard",
  busTypeId: "businessType",
  domainId: "dataDomain",
  busProcessId: "businessProcess",
  dwhId: "dwLayer",
  martId: "dataMart",
  subjectId: "subjectDomain",
  codeId: "codeStandard",
  codeDetailId: "codeDetail",
};
