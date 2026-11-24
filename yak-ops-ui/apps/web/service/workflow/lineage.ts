import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";

export interface WorkflowLineageRelation {
  sourceWorkFlowCode: number;
  targetWorkFlowCode: number;
}

export interface WorkflowLineageDetail {
  workFlowCode: number;
  workFlowName: string;
  workFlowPublishStatus?: string | null;
  scheduleStartTime?: string | null;
  scheduleEndTime?: string | null;
  crontab?: string | null;
  schedulePublishStatus?: number | null;
  sourceWorkFlowCode?: string | null;
}

export interface WorkflowLineage {
  workFlowRelationList: WorkflowLineageRelation[];
  workFlowRelationDetailList: WorkflowLineageDetail[];
}

const emptyLineage = (): WorkflowLineage => ({
  workFlowRelationList: [],
  workFlowRelationDetailList: [],
});

const baseUrl = () =>
  YAK_OPS_API_PREFIX +
  "/projects/" +
  encodeURIComponent(requireActiveProjectCode()) +
  "/lineages";

export async function getWorkflowLineage(
  workflowCode: number,
  signal?: AbortSignal,
): Promise<WorkflowLineage> {
  const response = await HttpUtils.getData<{ dataList?: WorkflowLineage }>(
    baseUrl() + "/" + encodeURIComponent(String(workflowCode)),
    { signal },
  );
  return response?.dataList ?? emptyLineage();
}

export async function listWorkflowLineageNames(
  workflowDefinitionName?: string,
  signal?: AbortSignal,
): Promise<WorkflowLineageDetail[]> {
  const params = new URLSearchParams();
  if (workflowDefinitionName?.trim()) {
    params.set("workflowDefinitionName", workflowDefinitionName.trim());
  }
  const query = params.toString();
  return HttpUtils.getData<WorkflowLineageDetail[]>(
    baseUrl() + "/query-by-name" + (query ? "?" + query : ""),
    { signal },
  );
}
