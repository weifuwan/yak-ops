import HttpUtils from "@/service/http/HttpUtils";

import { createModelingResourceApi, modelingResourcePath } from "../shared/modeling-http";
import type { ModelingRecord } from "../shared/types";

export const codeStandardApi = createModelingResourceApi("codeStandard", {
  listEndpoint: "list",
  updateWithId: true,
});

export const codeDetailApi = {
  all<T extends ModelingRecord>(projectCode: string, params: Record<string, string> = {}): Promise<T[]> {
    const codeId = params.codeId;
    if (!codeId) {
      return Promise.reject(new Error("查询标准代码明细时必须提供 codeId"));
    }
    return HttpUtils.getData<T[]>(
      `${modelingResourcePath(projectCode, "codeStandard")}/${encodeURIComponent(codeId)}/details`,
    );
  },
};
