import HttpUtils from "@/service/http/HttpUtils";

import { createModelingResourceApi, modelingResourcePath } from "../../shared/modeling-http";

export interface SubjectDomainDirectoryNode {
  key: string;
  value: string;
  title: string;
  parentId?: string | null;
  type: "MART" | "SUBJECT";
  children?: SubjectDomainDirectoryNode[];
}

export const subjectDomainApi = {
  ...createModelingResourceApi("subjectDomain", { allMethod: "POST" }),

  tree(projectCode: string): Promise<SubjectDomainDirectoryNode[]> {
    return HttpUtils.getData<SubjectDomainDirectoryNode[]>(
      `${modelingResourcePath(projectCode, "subjectDomain")}/tree`,
    );
  },
};
