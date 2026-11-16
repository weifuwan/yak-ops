export const planningQueryKeys = {
  dataMartTree: (projectCode: string, keyword = "") => ["modeling", "data-mart", "tree", projectCode, keyword] as const,
  dataMartTreePrefix: (projectCode: string) => ["modeling", "data-mart", "tree", projectCode] as const,
  dataMartDetail: (projectCode: string, id: string) => ["modeling", "data-mart", "detail", projectCode, id] as const,
  dataMartDetailPrefix: (projectCode: string) => ["modeling", "data-mart", "detail", projectCode] as const,
  dataMartChildren: (projectCode: string, id: string, pageNo: number) =>
    ["modeling", "data-mart", "children", projectCode, id, pageNo] as const,
  dataMartChildrenPrefix: (projectCode: string) => ["modeling", "data-mart", "children", projectCode] as const,
  dataMartSubjectDomains: (projectCode: string, id: string, pageNo: number) =>
    ["modeling", "data-mart", "subject-domains", projectCode, id, pageNo] as const,
  dataMartSubjectDomainsPrefix: (projectCode: string) =>
    ["modeling", "data-mart", "subject-domains", projectCode] as const,
  subjectDomainTreePrefix: (projectCode: string) => ["modeling", "subject-domain", "tree", projectCode] as const,
  subjectDomainDetail: (projectCode: string, id: string) => ["modeling", "subject-domain", "detail", projectCode, id] as const,
  subjectDomainDetailPrefix: (projectCode: string) => ["modeling", "subject-domain", "detail", projectCode] as const,
  subjectDomainChildren: (projectCode: string, id: string, pageNo: number) =>
    ["modeling", "subject-domain", "children", projectCode, id, pageNo] as const,
  subjectDomainChildrenPrefix: (projectCode: string) => ["modeling", "subject-domain", "children", projectCode] as const,
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