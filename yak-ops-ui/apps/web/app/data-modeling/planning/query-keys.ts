export const planningQueryKeys = {
  businessTypeTree: (projectCode: string, keyword = "") => ["modeling", "business-type", "tree", projectCode, keyword] as const,
  businessTypeTreePrefix: (projectCode: string) => ["modeling", "business-type", "tree", projectCode] as const,
  businessTypeDetail: (projectCode: string, id: string) => ["modeling", "business-type", "detail", projectCode, id] as const,
  businessTypeDetailPrefix: (projectCode: string) => ["modeling", "business-type", "detail", projectCode] as const,
  businessTypeChildren: (projectCode: string, parentId: string, pageNo: number, pageSize: number) =>
    ["modeling", "business-type", "children", projectCode, parentId, pageNo, pageSize] as const,
  businessTypeChildrenPrefix: (projectCode: string) => ["modeling", "business-type", "children", projectCode] as const,
  businessTypeRelatedDomains: (projectCode: string, businessTypeId: string) =>
    ["modeling", "business-type", "related-domains", projectCode, businessTypeId] as const,
  businessTypeRelatedDomainsPrefix: (projectCode: string) =>
    ["modeling", "business-type", "related-domains", projectCode] as const,
  businessTypeDataMarts: (projectCode: string, businessTypeId: string) =>
    ["modeling", "business-type", "data-marts", projectCode, businessTypeId] as const,
  businessTypeDataMartsPrefix: (projectCode: string) =>
    ["modeling", "business-type", "data-marts", projectCode] as const,
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