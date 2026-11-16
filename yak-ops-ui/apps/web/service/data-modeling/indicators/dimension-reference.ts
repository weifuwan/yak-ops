import HttpUtils from "@/service/http/HttpUtils";

import { modelingBasePath } from "../shared/modeling-http";
import type { ModelingRecord, ModelingSelectOption } from "../shared/types";

export interface ModelingDimensionFieldRecord extends ModelingRecord {
  tableId?: string;
  fieldName?: string;
  cnName?: string;
  codeStdId?: string;
}

export const dimensionReferenceApi = {
  dimensionOptions(projectCode: string): Promise<ModelingSelectOption[]> {
    return HttpUtils.getData<ModelingSelectOption[]>(
      `${modelingBasePath(projectCode)}/dimensions/options/select/dim`,
    );
  },

  fields(projectCode: string, tableId: string): Promise<ModelingDimensionFieldRecord[]> {
    return HttpUtils.getData<ModelingDimensionFieldRecord[]>(
      `${modelingBasePath(projectCode)}/dimensions/${encodeURIComponent(tableId)}/fields`,
    );
  },
};
