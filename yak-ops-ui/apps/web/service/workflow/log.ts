import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

const qs = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") search.set(key, String(value));
  });
  return search.toString();
};

export interface TaskInstanceLog {
  message: string;
  lineNum: number;
}

export const getTaskInstanceLog = (
  taskInstanceId: number,
  skipLineNum = 0,
  limit = 500,
  signal?: AbortSignal,
): Promise<TaskInstanceLog> =>
  HttpUtils.getData<TaskInstanceLog>(
    YAK_OPS_API_PREFIX +
      "/log/detail?" +
      qs({ taskInstanceId, skipLineNum, limit }),
    { signal, skipErrorHandler: true },
  );

export const downloadTaskInstanceLog = (taskInstanceId: number): Promise<Blob> =>
  HttpUtils.download(
    YAK_OPS_API_PREFIX +
      "/log/download-log?" +
      qs({ taskInstanceId }),
  );
