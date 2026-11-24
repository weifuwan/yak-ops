import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";

export type WorkflowWarningType = "NONE" | "SUCCESS" | "FAILURE" | "ALL";

export type WorkflowTaskDependType = "TASK_ONLY" | "TASK_PRE" | "TASK_POST";

export interface WorkflowAlertOptions {
  warningType: WorkflowWarningType;
  warningGroupId?: number;
}

export type WorkflowFailureStrategy = "END" | "CONTINUE";
export type WorkflowPriority = "HIGHEST" | "HIGH" | "MEDIUM" | "LOW" | "LOWEST";

export interface StartWorkflowOptions extends Partial<WorkflowAlertOptions> {
  workerGroup?: string;
  environmentCode?: number;
  tenantCode?: string;
  failureStrategy?: WorkflowFailureStrategy;
  workflowInstancePriority?: WorkflowPriority;
  startParams?: Record<string, string>;
  startNodeList?: number | string;
  taskDependType?: WorkflowTaskDependType;
}

const queryString = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") search.set(key, String(value));
  }
  return search.toString();
};

export async function startWorkflowInstance(
  workflowDefinitionCode: string,
  options?: StartWorkflowOptions,
): Promise<{ workflowInstanceId: number }> {
  const ids = await HttpUtils.postData<number[]>(
    YAK_OPS_API_PREFIX +
      "/projects/" +
      encodeURIComponent(requireActiveProjectCode()) +
      "/executors/start-workflow-instance?" +
      queryString({
        workflowDefinitionCode,
        execType: "START_PROCESS",
        failureStrategy: options?.failureStrategy ?? "END",
        startNodeList: options?.startNodeList,
        taskDependType: options?.taskDependType ?? "TASK_POST",
        warningType: options?.warningType ?? "NONE",
        warningGroupId:
          options?.warningType && options.warningType !== "NONE"
            ? options.warningGroupId
            : undefined,
        workflowInstancePriority: options?.workflowInstancePriority ?? "MEDIUM",
        workerGroup: options?.workerGroup || "default",
        tenantCode: options?.tenantCode || "default",
        environmentCode: options?.environmentCode ?? -1,
        dryRun: 0,
        startParams: options?.startParams ? JSON.stringify(options.startParams) : undefined,
      }),
    undefined,
  );
  if (!ids?.length) throw new Error("工作流启动未返回实例编号");
  return { workflowInstanceId: ids[0] };
}

export async function stopWorkflowInstance(workflowInstanceId: number): Promise<void> {
  await HttpUtils.postData<void>(
    YAK_OPS_API_PREFIX +
      "/projects/" +
      encodeURIComponent(requireActiveProjectCode()) +
      "/executors/execute?" +
      queryString({
        workflowInstanceId,
        executeType: "STOP",
      }),
    undefined,
  );
}

export type WorkflowBackfillRunMode = "RUN_MODE_SERIAL" | "RUN_MODE_PARALLEL";
export type WorkflowBackfillDependentMode = "OFF_MODE" | "ALL_DEPENDENT";
export type WorkflowBackfillExecutionOrder = "ASC_ORDER" | "DESC_ORDER";

export interface WorkflowBackfillOptions {
  backfillTime:
    | { complementStartDate: string; complementEndDate: string }
    | { complementScheduleDateList: string };
  runMode: WorkflowBackfillRunMode;
  expectedParallelismNumber: number;
  complementDependentMode: WorkflowBackfillDependentMode;
  allLevelDependent: boolean;
  executionOrder: WorkflowBackfillExecutionOrder;
  workerGroup?: string;
  environmentCode?: number;
  tenantCode?: string;
  failureStrategy?: WorkflowFailureStrategy;
  workflowInstancePriority?: WorkflowPriority;
  startParams?: Record<string, string>;
  startNodeList?: number | string;
  taskDependType?: WorkflowTaskDependType;
}

/** Submit a complement-data run through the existing DolphinScheduler executor endpoint. */
export async function startWorkflowBackfill(
  workflowDefinitionCode: string,
  options: WorkflowBackfillOptions,
): Promise<number[]> {
  const ids = await HttpUtils.postData<number[]>(
    YAK_OPS_API_PREFIX +
      "/projects/" +
      encodeURIComponent(requireActiveProjectCode()) +
      "/executors/start-workflow-instance?" +
      queryString({
        workflowDefinitionCode,
        execType: "COMPLEMENT_DATA",
        scheduleTime: JSON.stringify(options.backfillTime),
        failureStrategy: options.failureStrategy ?? "END",
        startNodeList: options.startNodeList,
        taskDependType: options.taskDependType ?? "TASK_POST",
        warningType: "NONE",
        workflowInstancePriority: options.workflowInstancePriority ?? "MEDIUM",
        workerGroup: options.workerGroup || "default",
        tenantCode: options.tenantCode || "default",
        environmentCode: options.environmentCode ?? -1,
        runMode: options.runMode,
        expectedParallelismNumber: options.expectedParallelismNumber,
        complementDependentMode: options.complementDependentMode,
        allLevelDependent: options.allLevelDependent ? "true" : "false",
        executionOrder: options.executionOrder,
        dryRun: 0,
        startParams: options.startParams ? JSON.stringify(options.startParams) : undefined,
      }),
    undefined,
  );
  if (!ids?.length) throw new Error("补数提交成功，但后端未返回工作流实例编号");
  return ids;
}
