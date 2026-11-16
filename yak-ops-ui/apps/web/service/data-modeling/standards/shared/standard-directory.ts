import HttpUtils from "@/service/http/HttpUtils";

import { createModelingResourceApi, modelingResourcePath } from "../../shared/modeling-http";
import type { ModelingAssetTreeNode } from "../../shared/types";

const resourceApi = createModelingResourceApi("standardDirectory", {
  listEndpoint: "list",
  updateWithId: true,
});

export const standardDirectoryApi = {
  ...resourceApi,

  tree(projectCode: string, module: "FIELD" | "CODE" | "UNIT"): Promise<ModelingAssetTreeNode[]> {
    return HttpUtils.getData<ModelingAssetTreeNode[]>(
      `${modelingResourcePath(projectCode, "standardDirectory")}/tree?module=${module}`,
    );
  },
};
