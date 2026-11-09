import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

export interface WorkflowTaskTypeRecord {
  taskType: string;
  taskCategory: string;
  collection: boolean;
}

export const listWorkflowTaskTypes = (
  signal?: AbortSignal,
): Promise<WorkflowTaskTypeRecord[]> =>
  HttpUtils.getData<WorkflowTaskTypeRecord[]>(
    `${YAK_OPS_API_PREFIX}/favourite/taskTypes`,
    { signal },
  );
