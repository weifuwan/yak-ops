import HttpUtils from "@/service/http/HttpUtils";

import { modelingBasePath } from "./shared/modeling-http";

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

export function getModelingBootstrap(projectCode: string): Promise<ModelingBootstrap> {
  return HttpUtils.getData<ModelingBootstrap>(`${modelingBasePath(projectCode)}/bootstrap`);
}
