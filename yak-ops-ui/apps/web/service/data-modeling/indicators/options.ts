import { businessProcessApi } from "../planning/common-layer/business-process";
import { dataDomainApi } from "../planning/common-layer/data-domain";
import { dataMartApi } from "../planning/application-layer/data-mart";
import { subjectDomainApi } from "../planning/application-layer/subject-domain";
import { businessTypeApi } from "../planning/business-type";
import { codeStandardApi } from "../standards/code-standard";
import { unitStandardApi } from "../standards/unit-standard";
import { atomIndicatorApi } from "./atom-indicator";
import { modifierApi } from "./modifier";
import { derivativeIndicatorApi } from "./derivative-indicator";
import { timePeriodApi, timePeriodCategoryApi } from "./time-period";
import type { IndicatorOptionResource } from "./index";
import type { ModelingRecord } from "../shared/types";

export async function getIndicatorOptionRecords(
  projectCode: string,
  resource: IndicatorOptionResource,
): Promise<ModelingRecord[]> {
  switch (resource) {
    case "businessType":
      return businessTypeApi.all<ModelingRecord>(projectCode);
    case "dataDomain":
      return dataDomainApi.all<ModelingRecord>(projectCode);
    case "businessProcess":
      return businessProcessApi.all<ModelingRecord>(projectCode);
    case "unitStandard":
      return unitStandardApi.all<ModelingRecord>(projectCode);
    case "atomIndicator":
      return atomIndicatorApi.all<ModelingRecord>(projectCode);
    case "timePeriod":
      return timePeriodApi.all<ModelingRecord>(projectCode);
    case "dataMart":
      return dataMartApi.all<ModelingRecord>(projectCode);
    case "subjectDomain":
      return subjectDomainApi.all<ModelingRecord>(projectCode);
    case "derivativeIndicator":
      return derivativeIndicatorApi.all<ModelingRecord>(projectCode);
    case "modifier":
      return modifierApi.all<ModelingRecord>(projectCode);
    case "codeStandard":
      return codeStandardApi.all<ModelingRecord>(projectCode);
    case "timePeriodCategory":
      return timePeriodCategoryApi.all<ModelingRecord>(projectCode);
    default:
      throw new Error("Unsupported indicator option resource");
  }
}
