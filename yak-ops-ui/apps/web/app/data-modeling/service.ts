import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";

export interface ModelingSection {
  code: string;
  name: string;
}

export interface ModelingBootstrap {
  projectCode: number;
  moduleCode: string;
  moduleName: string;
  sections: ModelingSection[];
}

export type ModelingResourceKey =
  | "businessType"
  | "dwLayer"
  | "dataDomain"
  | "businessProcess"
  | "dataMart"
  | "subjectDomain"
  | "standardDirectory"
  | "fieldStandard"
  | "codeStandard"
  | "codeDetail"
  | "unitStandard"
  | "dictionaryStandard"
  | "atomIndicator"
  | "derivativeIndicator"
  | "composeIndicator"
  | "modifier"
  | "timePeriodCategory"
  | "timePeriod";

export interface ModelingVersionRecord {
  id: string;
  versionNumber: number;
  versionType: string;
  updateTime?: string;
  updateBy?: string;
}

export interface ModelingAssetTreeNode {
  id: string;
  key: string;
  value: string;
  title: string;
  parentId?: string;
  type: string;
  children: ModelingAssetTreeNode[];
}

export interface ModelingRecord {
  id?: string;
  createTime?: string;
  updateTime?: string;
  [key: string]: unknown;
}

const RESOURCE_PATH: Record<ModelingResourceKey, string> = {
  businessType: "business-type",
  dwLayer: "dw-layer",
  dataDomain: "data-domain",
  businessProcess: "business-process",
  dataMart: "data-mart",
  subjectDomain: "subject-domain",
  standardDirectory: "assets/standard-directory",
  fieldStandard: "assets/field-standard",
  codeStandard: "assets/code-standard",
  codeDetail: "assets/code-detail",
  unitStandard: "assets/unit-standard",
  dictionaryStandard: "assets/dictionary-standard",
  atomIndicator: "assets/atom-indicator",
  derivativeIndicator: "assets/derivative-indicator",
  composeIndicator: "assets/compose-indicator",
  modifier: "assets/modifier",
  timePeriodCategory: "assets/time-period-category",
  timePeriod: "assets/time-period",
};

function modelingPath(projectCode: string, resource?: ModelingResourceKey): string {
  const base = `${YAK_OPS_API_PREFIX}/projects/${encodeURIComponent(projectCode)}/modeling`;
  return resource ? `${base}/${RESOURCE_PATH[resource]}` : base;
}

export const modelingQueryKeys = {
  dataDomainAll: (projectCode: string) => ["modeling", "data-domain", "all", projectCode] as const,
  businessProcessPage: (projectCode: string) => ["modeling", "business-process", "page", projectCode] as const,
  businessProcessFilterOptions: (projectCode: string) => ["modeling", "business-process", "filter-options", projectCode] as const,
  businessProcessAll: (projectCode: string) => ["modeling", "business-process", "all", projectCode] as const,
  dataDomainBusinessProcesses: (projectCode: string) => ["modeling", "data-domain", "business-processes", projectCode] as const,
  dataMartAll: (projectCode: string) => ["modeling", "data-mart", "all", projectCode] as const,
  dataMartBusinessTypes: (projectCode: string) => ["modeling", "data-mart", "business-types", projectCode] as const,
  subjectDomainTree: (projectCode: string) => ["modeling", "subject-domain", "tree", projectCode] as const,
  subjectDomainDataMarts: (projectCode: string) => ["modeling", "subject-domain", "data-marts", projectCode] as const,
};

export function getModelingBootstrap(projectCode: string): Promise<ModelingBootstrap> {
  return HttpUtils.getData<ModelingBootstrap>(`${modelingPath(projectCode)}/bootstrap`);
}

export const modelingEntityApi = {
  page<T extends ModelingRecord>(
    projectCode: string,
    resource: ModelingResourceKey,
    params: Record<string, unknown>,
  ): Promise<PageInfo<T>> {
    return HttpUtils.postData<PageInfo<T>>(
      `${modelingPath(projectCode, resource)}/page`,
      params,
    );
  },

  all<T extends ModelingRecord>(
    projectCode: string,
    resource: ModelingResourceKey,
    params: Record<string, string> = {},
  ): Promise<T[]> {
    const query = new URLSearchParams(params).toString();
    const url = `${modelingPath(projectCode, resource)}/all${query ? `?${query}` : ""}`;
    return resource === "subjectDomain"
      ? HttpUtils.postData<T[]>(url, {})
      : HttpUtils.getData<T[]>(url);
  },

  directoryTree(projectCode: string, module: "FIELD" | "CODE" | "UNIT"): Promise<ModelingAssetTreeNode[]> {
    return HttpUtils.getData<ModelingAssetTreeNode[]>(
      `${modelingPath(projectCode, "standardDirectory")}/tree?module=${module}`,
    );
  },

  getById<T extends ModelingRecord>(
    projectCode: string,
    resource: ModelingResourceKey,
    id: string,
  ): Promise<T> {
    return HttpUtils.getData<T>(
      `${modelingPath(projectCode, resource)}/${encodeURIComponent(id)}`,
    );
  },

  create(
    projectCode: string,
    resource: ModelingResourceKey,
    payload: Record<string, unknown>,
  ): Promise<string> {
    return HttpUtils.postData<string>(modelingPath(projectCode, resource), payload);
  },

  update<T extends ModelingRecord>(
    projectCode: string,
    resource: ModelingResourceKey,
    payload: Record<string, unknown>,
  ): Promise<T> {
    return HttpUtils.putData<T>(modelingPath(projectCode, resource), payload);
  },

  remove(projectCode: string, resource: ModelingResourceKey, id: string): Promise<string> {
    return HttpUtils.deleteData<string>(
      `${modelingPath(projectCode, resource)}/${encodeURIComponent(id)}`,
    );
  },

  submit(projectCode: string, resource: ModelingResourceKey, id: string): Promise<ModelingRecord> {
    return HttpUtils.postData<ModelingRecord>(
      `${modelingPath(projectCode, resource)}/${encodeURIComponent(id)}/submit`,
      {},
    );
  },

  versions(projectCode: string, resource: ModelingResourceKey, id: string): Promise<ModelingVersionRecord[]> {
    return HttpUtils.getData<ModelingVersionRecord[]>(
      `${modelingPath(projectCode, resource)}/${encodeURIComponent(id)}/versions`,
    );
  },

  rollback(
    projectCode: string,
    resource: ModelingResourceKey,
    id: string,
    versionNumber: number,
  ): Promise<ModelingRecord> {
    return HttpUtils.postData<ModelingRecord>(
      `${modelingPath(projectCode, resource)}/${encodeURIComponent(id)}/rollback/${versionNumber}`,
      {},
    );
  },
};

export interface BusinessTypeTreeNode {
  key: string;
  value: string;
  title: string;
  parentId?: string | null;
  children?: BusinessTypeTreeNode[];
}

export const modelingBusinessTypeApi = {
  tree(projectCode: string, keyword = ""): Promise<BusinessTypeTreeNode[]> {
    return HttpUtils.getData<BusinessTypeTreeNode[]>(
      `${modelingPath(projectCode, "businessType")}/tree?keyword=${encodeURIComponent(keyword)}`,
    );
  },
  selectRelatedDomains<T extends ModelingRecord>(projectCode: string, id: string): Promise<T[]> {
    return HttpUtils.getData<T[]>(
      `${modelingPath(projectCode, "businessType")}/select/related-domain/${encodeURIComponent(id)}`,
    );
  },
  allRelatedDomainIds(projectCode: string): Promise<Record<string, string[]>> {
    return HttpUtils.getData<Record<string, string[]>>(
      `${modelingPath(projectCode, "businessType")}/related-domain/all`,
    );
  },
  updateRelatedDomains(projectCode: string, id: string, domainIds: string[]): Promise<boolean> {
    return HttpUtils.putData<boolean>(
      `${modelingPath(projectCode, "businessType")}/update/related-domain/${encodeURIComponent(id)}`,
      domainIds,
    );
  },
  deleteRelatedDomain(projectCode: string, id: string, domainId: string): Promise<boolean> {
    return HttpUtils.deleteData<boolean>(
      `${modelingPath(projectCode, "businessType")}/delete/related-domain/${encodeURIComponent(id)}?dataDomainId=${encodeURIComponent(domainId)}`,
    );
  },
  loadAllDomains(projectCode: string, id: string): Promise<boolean> {
    return HttpUtils.putData<boolean>(
      `${modelingPath(projectCode, "businessType")}/loadall/related-domain/${encodeURIComponent(id)}`,
    );
  },
};


export interface DataMartDirectoryNode {
  key: string;
  value: string;
  title: string;
  parentId?: string | null;
  children?: DataMartDirectoryNode[];
}

export interface SubjectDomainDirectoryNode {
  key: string;
  value: string;
  title: string;
  parentId?: string | null;
  type: "MART" | "SUBJECT";
  children?: SubjectDomainDirectoryNode[];
}

export const modelingDataMartApi = {
  tree(projectCode: string, keyword = ""): Promise<DataMartDirectoryNode[]> {
    return HttpUtils.getData<DataMartDirectoryNode[]>(
      `${modelingPath(projectCode, "dataMart")}/tree?keyword=${encodeURIComponent(keyword)}`,
    );
  },
};

export const modelingSubjectDomainApi = {
  tree(projectCode: string): Promise<SubjectDomainDirectoryNode[]> {
    return HttpUtils.getData<SubjectDomainDirectoryNode[]>(
      `${modelingPath(projectCode, "subjectDomain")}/tree`,
    );
  },
};
