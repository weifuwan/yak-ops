import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

export interface DataSyncProject {
  code: string;
  name: string;
}

const PROJECT_KEY = "yak-ops:data-sync:project";

export const selectedDataSyncProject = () => sessionStorage.getItem(PROJECT_KEY) ?? "";

export const selectDataSyncProject = (code: string) => sessionStorage.setItem(PROJECT_KEY, code);

export const listDataSyncProjects = async (): Promise<DataSyncProject[]> => {
  const projects = await HttpUtils.getData<Array<{ code: string | number; name: string }>>(
    YAK_OPS_API_PREFIX + "/projects/created-and-authed",
  );
  return projects.map((project) => ({ code: String(project.code), name: project.name }));
};

export const currentDataSyncProject = async (): Promise<string> => {
  const projects = await listDataSyncProjects();
  const selected = selectedDataSyncProject();
  const project = projects.find((item) => item.code === selected) ?? projects[0];
  if (!project) throw new Error("请先创建或加入一个项目");
  return project.code;
};

export const dataSyncApi = async () =>
  YAK_OPS_API_PREFIX + "/projects/" + encodeURIComponent(await currentDataSyncProject()) + "/data-sync";
