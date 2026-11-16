import HttpUtils from "@/service/http/HttpUtils";

import { createModelingResourceApi, modelingResourcePath } from "../../shared/modeling-http";

export interface BusinessProcessCascaderNode {
  key: string;
  parentId?: string | null;
  label: string;
  value: string;
  children?: BusinessProcessCascaderNode[];
}

const resourceApi = createModelingResourceApi("businessProcess");

export const businessProcessApi = {
  ...resourceApi,

  cascaderOption(projectCode: string, busTypeId?: string): Promise<BusinessProcessCascaderNode[]> {
    const query = busTypeId ? "?busTypeId=" + encodeURIComponent(busTypeId) : "";
    return HttpUtils.getData<BusinessProcessCascaderNode[]>(
      modelingResourcePath(projectCode, "businessProcess") + "/option/cascader" + query,
    );
  },
};
