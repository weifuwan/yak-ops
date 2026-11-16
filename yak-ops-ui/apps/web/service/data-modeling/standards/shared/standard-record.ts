import type { ModelingResourceApi } from "../../shared/modeling-http";
import { codeStandardApi } from "../code-standard";
import { fieldStandardApi } from "../field-standard";
import { unitStandardApi } from "../unit-standard";

export type StandardRecordResource = "fieldStandard" | "codeStandard" | "unitStandard";

const STANDARD_RECORD_APIS: Record<StandardRecordResource, ModelingResourceApi> = {
  fieldStandard: fieldStandardApi,
  codeStandard: codeStandardApi,
  unitStandard: unitStandardApi,
};

export function getStandardRecordApi(resource: StandardRecordResource): ModelingResourceApi {
  return STANDARD_RECORD_APIS[resource];
}
