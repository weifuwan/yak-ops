export const planningQueryKeys = {
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
