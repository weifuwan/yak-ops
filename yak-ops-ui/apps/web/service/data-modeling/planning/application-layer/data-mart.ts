import HttpUtils from "@/service/http/HttpUtils";

import { createModelingResourceApi, modelingResourcePath } from "../../shared/modeling-http";

export interface DataMartDirectoryNode {
  key: string;
  value: string;
  title: string;
  parentId?: string | null;
  children?: DataMartDirectoryNode[];
}

export const dataMartApi = {
  ...createModelingResourceApi("dataMart"),

  tree(projectCode: string, keyword = ""): Promise<DataMartDirectoryNode[]> {
    return HttpUtils.getData<DataMartDirectoryNode[]>(
      `${modelingResourcePath(projectCode, "dataMart")}/tree?keyword=${encodeURIComponent(keyword)}`,
    );
  },
};
