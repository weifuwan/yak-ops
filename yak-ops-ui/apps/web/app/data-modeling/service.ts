import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

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
  return HttpUtils.getData<ModelingBootstrap>(
    `${YAK_OPS_API_PREFIX}/projects/${encodeURIComponent(projectCode)}/modeling/bootstrap`,
  );
}
