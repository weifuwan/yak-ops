import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";

export type WorkflowWarningType = "NONE" | "SUCCESS" | "FAILURE" | "ALL";

export interface StartWorkflowOptions {
  warningType?: WorkflowWarningType;
  warningGroupId?: number;
  workerGroup?: string;
  startParams?: Record<string, string>;
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
        failureStrategy: "END",
        taskDependType: "TASK_POST",
        warningType: options?.warningType ?? "NONE",
        warningGroupId:
          options?.warningType && options.warningType !== "NONE"
            ? options.warningGroupId
            : undefined,
        workflowInstancePriority: "MEDIUM",
        workerGroup: options?.workerGroup || "default",
        tenantCode: "default",
        dryRun: 0,
        startParams: options?.startParams ? JSON.stringify(options.startParams) : undefined,
      }),
    undefined,
  );
  if (!ids?.length) throw new Error("工作流启动未返回实例编号");
  return { workflowInstanceId: ids[0] };
}
