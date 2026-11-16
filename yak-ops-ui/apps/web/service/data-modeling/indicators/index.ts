import { atomIndicatorApi } from "./atom-indicator";
import { compositeIndicatorApi } from "./composite-indicator";
import { derivativeIndicatorApi } from "./derivative-indicator";
import { modifierApi } from "./modifier";
import { timePeriodApi } from "./time-period";

export type IndicatorResourceKind =
  | "atomIndicator"
  | "derivativeIndicator"
  | "composeIndicator"
  | "modifier"
  | "timePeriod";

export type IndicatorOptionResource =
  | "businessType"
  | "dataDomain"
  | "businessProcess"
  | "unitStandard"
  | "atomIndicator"
  | "timePeriod"
  | "dwLayer"
  | "dataMart"
  | "subjectDomain"
  | "derivativeIndicator"
  | "codeStandard"
  | "timePeriodCategory"
  | "codeDetail";

export function getIndicatorApi(kind: IndicatorResourceKind) {
  switch (kind) {
    case "atomIndicator":
      return atomIndicatorApi;
    case "derivativeIndicator":
      return derivativeIndicatorApi;
    case "composeIndicator":
      return compositeIndicatorApi;
    case "modifier":
      return modifierApi;
    case "timePeriod":
      return timePeriodApi;
  }
  return timePeriodApi;
}

