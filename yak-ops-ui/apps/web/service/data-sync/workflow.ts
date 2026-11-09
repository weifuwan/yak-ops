import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";
import { YAK_OPS_API_PREFIX } from "@/config/api";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";

import type { WorkflowExecutionStatus } from "./workflow-state";
export { isWorkflowActive, canStopWorkflow, workflowStatusText } from "./workflow-state";
export type { WorkflowExecutionStatus } from "./workflow-state";

export interface DataSyncWorkflowInstance {
  id: number;
  workflowDefinitionCode: number | string;
  state: WorkflowExecutionStatus;
  startTime?: string | null;
  endTime?: string | null;
  stateHistory?: string | null;
}

const workflowUrl = (path: string) =>
  YAK_OPS_API_PREFIX +
  "/projects/" +
  encodeURIComponent(requireActiveProjectCode()) +
  "/workflow-instances" +
  path;

export const getLatestDataSyncWorkflow = async (
  workflowCode: string,
  signal?: AbortSignal,
): Promise<DataSyncWorkflowInstance | null> => {
  const params = new URLSearchParams({
    workflowDefinitionCode: workflowCode,
    pageNo: "1",
    pageSize: "1",
  });
  const page = await HttpUtils.getData<PageInfo<DataSyncWorkflowInstance>>(
    workflowUrl("?" + params.toString()),
    { signal },
  );
  return page.totalList[0] ?? null;
};

export const getDataSyncWorkflowInstance = (
  workflowInstanceId: number,
  signal?: AbortSignal,
): Promise<DataSyncWorkflowInstance> =>
  HttpUtils.getData<DataSyncWorkflowInstance>(
    workflowUrl("/" + workflowInstanceId),
    { signal },
  );
