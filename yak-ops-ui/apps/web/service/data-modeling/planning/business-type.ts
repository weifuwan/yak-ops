import HttpUtils from "@/service/http/HttpUtils";

import { createModelingResourceApi, modelingResourcePath } from "../shared/modeling-http";
import type { ModelingRecord } from "../shared/types";

export interface BusinessTypeTreeNode {
  key: string;
  value: string;
  title: string;
  parentId?: string | null;
  children?: BusinessTypeTreeNode[];
}

const resourceApi = createModelingResourceApi("businessType");

export const businessTypeApi = {
  ...resourceApi,

  tree(projectCode: string, keyword = ""): Promise<BusinessTypeTreeNode[]> {
    return HttpUtils.getData<BusinessTypeTreeNode[]>(
      `${modelingResourcePath(projectCode, "businessType")}/tree?keyword=${encodeURIComponent(keyword)}`,
    );
  },

  selectRelatedDomains<T extends ModelingRecord>(projectCode: string, id: string): Promise<T[]> {
    return HttpUtils.getData<T[]>(
      `${modelingResourcePath(projectCode, "businessType")}/select/related-domain/${encodeURIComponent(id)}`,
    );
  },

  allRelatedDomainIds(projectCode: string): Promise<Record<string, string[]>> {
    return HttpUtils.getData<Record<string, string[]>>(
      `${modelingResourcePath(projectCode, "businessType")}/related-domain/all`,
    );
  },

  updateRelatedDomains(projectCode: string, id: string, domainIds: string[]): Promise<boolean> {
    return HttpUtils.putData<boolean>(
      `${modelingResourcePath(projectCode, "businessType")}/update/related-domain/${encodeURIComponent(id)}`,
      domainIds,
    );
  },

  deleteRelatedDomain(projectCode: string, id: string, domainId: string): Promise<boolean> {
    return HttpUtils.deleteData<boolean>(
      `${modelingResourcePath(projectCode, "businessType")}/delete/related-domain/${encodeURIComponent(id)}?dataDomainId=${encodeURIComponent(domainId)}`,
    );
  },

  loadAllDomains(projectCode: string, id: string): Promise<boolean> {
    return HttpUtils.putData<boolean>(
      `${modelingResourcePath(projectCode, "businessType")}/loadall/related-domain/${encodeURIComponent(id)}`,
    );
  },
};
