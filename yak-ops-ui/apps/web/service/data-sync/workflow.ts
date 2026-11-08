import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";
import { YAK_OPS_API_PREFIX } from "@/config/api";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";

export type WorkflowExecutionStatus =
  | "SUBMITTED_SUCCESS"
  | "RUNNING_EXECUTION"
  | "READY_PAUSE"
  | "PAUSE"
  | "READY_STOP"
  | "STOP"
  | "FAILURE"
  | "SUCCESS"
  | "SERIAL_WAIT"
  | "FAILOVER";

export interface DataSyncWorkflowInstance {
  id: number;
  workflowDefinitionCode: number | string;
  state: WorkflowExecutionStatus;
  startTime?: string | null;
  endTime?: string | null;
  stateHistory?: string | null;
}

export const isWorkflowActive = (state: WorkflowExecutionStatus): boolean =>
  state !== "STOP" && state !== "FAILURE" && state !== "SUCCESS";

export const canStopWorkflow = (state: WorkflowExecutionStatus): boolean =>
  state === "SERIAL_WAIT" ||
  state === "RUNNING_EXECUTION" ||
  state === "READY_PAUSE" ||
  state === "READY_STOP";

export const workflowStatusText = (state: WorkflowExecutionStatus): string =>
  ({
    SUBMITTED_SUCCESS: "启动中",
    SERIAL_WAIT: "排队中",
    RUNNING_EXECUTION: "启动中",
    READY_PAUSE: "暂停中",
    PAUSE: "已暂停",
    READY_STOP: "停止中",
    STOP: "已停止",
    FAILURE: "运行失败",
    SUCCESS: "已完成",
    FAILOVER: "恢复中",
  })[state];

const workflowUrl = (path: string) =>
  YAK_OPS_API_PREFIX +
  "/projects/" +
  encodeURIComponent(requireActiveProjectCode()) +
  "/workflow-instances" +
  path;

export const getLatestDataSyncWorkflow = async (
  workflowCode: string,
  signal?: AbortSignal,
): Promise<DataSyncWorkflowInstance | undefined> => {
  const params = new URLSearchParams({
    workflowDefinitionCode: workflowCode,
    pageNo: "1",
    pageSize: "1",
  });
  const page = await HttpUtils.getData<PageInfo<DataSyncWorkflowInstance>>(
    workflowUrl("?" + params.toString()),
    { signal },
  );
  return page.totalList[0];
};

export const getDataSyncWorkflowInstance = (
  workflowInstanceId: number,
  signal?: AbortSignal,
): Promise<DataSyncWorkflowInstance> =>
  HttpUtils.getData<DataSyncWorkflowInstance>(
    workflowUrl("/" + workflowInstanceId),
    { signal },
  );
