import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";

export interface ProjectParameterRecord {
  code: number;
  projectCode: number;
  paramName: string;
  paramValue?: string | null;
  paramDataType?: string | null;
}

export const listProjectParameters = (
  projectCode: number,
  signal?: AbortSignal,
): Promise<PageInfo<ProjectParameterRecord>> =>
  HttpUtils.getData<PageInfo<ProjectParameterRecord>>(
    YAK_OPS_API_PREFIX +
      "/projects/" +
      encodeURIComponent(String(projectCode)) +
      "/project-parameter?pageNo=1&pageSize=100",
    { signal },
  );
