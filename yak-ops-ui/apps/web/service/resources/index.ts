import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

export interface WorkflowResourceNode {
  name?: string;
  currentDir?: string;
  fullName?: string;
  description?: string;
  isDirctory?: boolean;
  dirctory?: boolean;
  isDirectory?: boolean;
  children?: WorkflowResourceNode[];
}

export const listWorkflowResources = (
  signal?: AbortSignal,
): Promise<WorkflowResourceNode[]> =>
  HttpUtils.getData<WorkflowResourceNode[]>(
    YAK_OPS_API_PREFIX + "/resources/list?type=FILE",
    { signal },
  );
