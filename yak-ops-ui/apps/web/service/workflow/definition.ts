import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";

export interface WorkflowDefinitionRecord {
  id: number;
  code: number;
  name: string;
  version: number;
  releaseState: string;
  projectCode: number;
  description?: string | null;
  globalParams?: string | null;
  locations?: string | null;
  timeout?: number;
  createTime?: string | null;
  updateTime?: string | null;
  userId: number;
  userName?: string | null;
  modifyBy?: string | null;
  executionType?: string | null;
}

export interface WorkflowTaskRecord {
  id: number;
  code: number;
  name: string;
  version: number;
  description?: string | null;
  projectCode: number;
  userId: number;
  taskType: string;
  createTime?: string | null;
  updateTime?: string | null;
  modifyBy?: string | null;
  taskParams?: unknown;
  flag?: string | null;
  taskPriority?: string | null;
  workerGroup?: string | null;
  environmentCode?: number;
  failRetryTimes?: number;
  failRetryInterval?: number;
  timeoutFlag?: string | null;
  timeoutNotifyStrategy?: string | null;
  timeout?: number;
  delayTime?: number;
  taskGroupId?: number;
  taskGroupPriority?: number;
  cpuQuota?: number | null;
  memoryMax?: number | null;
  taskExecuteType?: string | null;
}

export interface WorkflowTaskRelationRecord {
  id: number;
  name?: string | null;
  workflowDefinitionVersion: number;
  projectCode: number;
  workflowDefinitionCode: number;
  preTaskCode: number;
  preTaskVersion: number;
  postTaskCode: number;
  postTaskVersion: number;
}

export interface WorkflowDagRecord {
  workflowDefinition: WorkflowDefinitionRecord;
  workflowTaskRelationList: WorkflowTaskRelationRecord[];
  taskDefinitionList: WorkflowTaskRecord[];
}

export type WorkflowTaskDefinitionPatch = Partial<
  Pick<
    WorkflowTaskRecord,
    "taskParams" | "taskGroupId" | "taskGroupPriority"
  >
>;

export interface WorkflowDefinitionPageParams {
  pageNo: number;
  pageSize: number;
  searchVal?: string;
  userId?: number;
}

export interface WorkflowDefinitionCreatePayload {
  name: string;
  description?: string;
}

export type WorkflowDefinitionSettingsPatch = Partial<
  Pick<
    WorkflowDefinitionRecord,
    "name" | "description" | "globalParams" | "timeout" | "executionType"
  >
>;

const qp = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") search.set(key, String(value));
  }
  return search.toString();
};

const baseUrl = () =>
  YAK_OPS_API_PREFIX +
  "/projects/" +
  encodeURIComponent(requireActiveProjectCode()) +
  "/workflow-definition";

export const listWorkflowDefinitions = (
  params: WorkflowDefinitionPageParams,
  signal?: AbortSignal,
): Promise<PageInfo<WorkflowDefinitionRecord>> =>
  HttpUtils.getData<PageInfo<WorkflowDefinitionRecord>>(
    baseUrl() +
      "?" +
      qp({
        pageNo: params.pageNo,
        pageSize: params.pageSize,
        searchVal: params.searchVal,
        userId: params.userId,
      }),
    { signal },
  );

export const createWorkflowDefinition = (
  payload: WorkflowDefinitionCreatePayload,
): Promise<WorkflowDefinitionRecord> =>
  HttpUtils.postData<WorkflowDefinitionRecord>(baseUrl(), undefined, {
    body: new URLSearchParams({
      name: payload.name,
      description: payload.description ?? "",
      globalParams: "[]",
      locations: "[]",
      timeout: "0",
      taskRelationJson: "[]",
      taskDefinitionJson: "[]",
      executionType: "PARALLEL",
    }),
  });

export const listWorkflowTasks = (
  workflowCode: number,
  signal?: AbortSignal,
): Promise<WorkflowTaskRecord[]> =>
  HttpUtils.getData<WorkflowTaskRecord[]>(
    baseUrl() + "/" + encodeURIComponent(String(workflowCode)) + "/tasks",
    { signal },
  );


export const getWorkflowDefinition = (
  workflowCode: number,
  signal?: AbortSignal,
): Promise<WorkflowDagRecord> =>
  HttpUtils.getData<WorkflowDagRecord>(
    baseUrl() + "/" + encodeURIComponent(String(workflowCode)),
    { signal },
  );

const updateWorkflowDefinitionGraph = (
  dag: WorkflowDagRecord,
  patch: WorkflowDefinitionSettingsPatch,
  taskDefinitionList: WorkflowTaskRecord[] = dag.taskDefinitionList,
): Promise<unknown> => {
  const workflow = dag.workflowDefinition;
  const body = new URLSearchParams({
    name: patch.name ?? workflow.name,
    code: String(workflow.code),
    description: patch.description ?? workflow.description ?? "",
    globalParams: patch.globalParams ?? workflow.globalParams ?? "[]",
    locations: workflow.locations ?? "",
    timeout: String(patch.timeout ?? workflow.timeout ?? 0),
    taskRelationJson: JSON.stringify(dag.workflowTaskRelationList ?? []),
    taskDefinitionJson: JSON.stringify(taskDefinitionList ?? []),
    executionType: patch.executionType ?? workflow.executionType ?? "PARALLEL",
  });

  return HttpUtils.putData<unknown>(
    baseUrl() + "/" + encodeURIComponent(String(workflow.code)),
    undefined,
    { body },
  );
};

export const updateWorkflowDefinitionSettings = async (
  workflowCode: number,
  patch: WorkflowDefinitionSettingsPatch,
): Promise<void> => {
  const dag = await getWorkflowDefinition(workflowCode);
  await updateWorkflowDefinitionGraph(dag, patch);
};

export const renameWorkflowDefinition = async (
  workflowCode: number,
  name: string,
): Promise<void> => {
  const dag = await getWorkflowDefinition(workflowCode);
  await updateWorkflowDefinitionGraph(dag, { name });
};

export const renameWorkflowTask = async (
  workflowCode: number,
  taskCode: number,
  name: string,
): Promise<void> => {
  const dag = await getWorkflowDefinition(workflowCode);
  const task = dag.taskDefinitionList.find((item) => item.code === taskCode);
  if (!task) throw new Error("工作流节点不存在或已被删除");

  const taskDefinitionList = dag.taskDefinitionList.map((item) =>
    item.code === taskCode ? { ...item, name } : item,
  );
  await updateWorkflowDefinitionGraph(dag, { name: dag.workflowDefinition.name }, taskDefinitionList);
};

export const updateWorkflowTaskDefinition = async (
  workflowCode: number,
  taskCode: number,
  patch: WorkflowTaskDefinitionPatch,
): Promise<void> => {
  const dag = await getWorkflowDefinition(workflowCode);
  const taskExists = dag.taskDefinitionList.some(
    (item) => item.code === taskCode,
  );

  if (!taskExists) {
    throw new Error("工作流节点不存在或已被删除");
  }

  const taskDefinitionList = dag.taskDefinitionList.map((item) =>
    item.code === taskCode ? { ...item, ...patch } : item,
  );

  await updateWorkflowDefinitionGraph(
    dag,
    { name: dag.workflowDefinition.name },
    taskDefinitionList,
  );
};

export const updateWorkflowTaskGroupSettings = async (
  workflowCode: number,
  taskGroupId: number,
  taskGroupPriority: number,
): Promise<void> => {
  const dag = await getWorkflowDefinition(workflowCode);
  const taskDefinitionList = (dag.taskDefinitionList ?? []).map((task) => ({
    ...task,
    taskGroupId,
    taskGroupPriority,
  }));

  await updateWorkflowDefinitionGraph(
    dag,
    { name: dag.workflowDefinition.name },
    taskDefinitionList,
  );
};

export const removeWorkflowTask = async (
  workflowCode: number,
  taskCode: number,
): Promise<void> => {
  const dag = await getWorkflowDefinition(workflowCode);
  const taskExists = dag.taskDefinitionList.some(
    (task) => task.code === taskCode,
  );

  if (!taskExists) {
    throw new Error("工作流节点不存在或已被移除");
  }
  if (dag.taskDefinitionList.length <= 1) {
    throw new Error("工作流至少需要保留一个节点");
  }

  const taskDefinitionList = dag.taskDefinitionList.filter(
    (task) => task.code !== taskCode,
  );
  const nextDag: WorkflowDagRecord = {
    ...dag,
    taskDefinitionList,
    workflowTaskRelationList: dag.workflowTaskRelationList.filter(
      (relation) =>
        relation.preTaskCode !== taskCode &&
        relation.postTaskCode !== taskCode,
    ),
  };

  await updateWorkflowDefinitionGraph(
    nextDag,
    { name: dag.workflowDefinition.name },
    taskDefinitionList,
  );
};

export const copyWorkflowDefinition = (
  workflowCode: number,
  targetProjectCode: number,
): Promise<void> =>
  HttpUtils.postData<void>(
    baseUrl() +
      "/batch-copy?" +
      qp({ codes: workflowCode, targetProjectCode }),
  );

export const moveWorkflowDefinition = (
  workflowCode: number,
  targetProjectCode: number,
): Promise<void> =>
  HttpUtils.postData<void>(
    baseUrl() +
      "/batch-move?" +
      qp({ codes: workflowCode, targetProjectCode }),
  );

export const deleteWorkflowDefinition = (workflowCode: number): Promise<void> =>
  HttpUtils.deleteData<void>(
    baseUrl() + "/" + encodeURIComponent(String(workflowCode)),
  );
