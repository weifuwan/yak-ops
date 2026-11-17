import type { ModelingResourceApi } from "../shared/modeling-http";
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
  | "dataMart"
  | "subjectDomain"
  | "derivativeIndicator"
  | "codeStandard"
  | "timePeriodCategory"
  | "codeDetail"
  | "modifier";

const INDICATOR_APIS: Record<IndicatorResourceKind, ModelingResourceApi> = {
  atomIndicator: atomIndicatorApi,
  derivativeIndicator: derivativeIndicatorApi,
  composeIndicator: compositeIndicatorApi,
  modifier: modifierApi,
  timePeriod: timePeriodApi,
};

export function getIndicatorApi(kind: IndicatorResourceKind): ModelingResourceApi {
  return INDICATOR_APIS[kind];
}
