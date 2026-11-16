import { codeStandardApi } from "../code-standard";
import { fieldStandardApi } from "../field-standard";
import { unitStandardApi } from "../unit-standard";

export type StandardRecordResource = "fieldStandard" | "codeStandard" | "unitStandard";

export function getStandardRecordApi(resource: StandardRecordResource) {
  switch (resource) {
    case "fieldStandard":
      return fieldStandardApi;
    case "codeStandard":
      return codeStandardApi;
    case "unitStandard":
      return unitStandardApi;
    default:
      return unitStandardApi;
  }
}
