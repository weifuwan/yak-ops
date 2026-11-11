import type {
  WorkflowDagRecord,
  WorkflowTaskRecord,
} from "@/service/workflow/definition";

function parseJsonValue(value: unknown, fallback: unknown): unknown {
  if (value === null || value === undefined || value === "") {
    return fallback;
  }

  if (typeof value !== "string") {
    return value;
  }

  try {
    return JSON.parse(value) as unknown;
  } catch {
    return value;
  }
}

function buildNode(task: WorkflowTaskRecord) {
  return {
    code: task.code,
    name: task.name,
    type: task.taskType,
    version: task.version,
    description: task.description ?? "",
    // The workflow-definition API masks sensitive values before returning taskParams.
    params: parseJsonValue(task.taskParams, {}),
    execution: {
      workerGroup: task.workerGroup,
      environmentCode: task.environmentCode,
      executeType: task.taskExecuteType,
    },
    retry: {
      times: task.failRetryTimes,
      intervalMinutes: task.failRetryInterval,
    },
    timeout: {
      flag: task.timeoutFlag,
      minutes: task.timeout,
      notifyStrategy: task.timeoutNotifyStrategy,
    },
    scheduling: {
      priority: task.taskPriority,
      delayMinutes: task.delayTime,
      groupId: task.taskGroupId,
      groupPriority: task.taskGroupPriority,
    },
    resources: {
      cpuQuota: task.cpuQuota,
      memoryMax: task.memoryMax,
    },
    status: task.flag,
  };
}

/**
 * Build the read-only, frontend-facing Spec from the existing workflow DAG API.
 * Schedule data is intentionally excluded because it is managed by a separate API.
 */
export function buildWorkflowSpec(dag: WorkflowDagRecord) {
  const workflow = dag.workflowDefinition;

  return {
    apiVersion: "yak-ops.io/v1",
    kind: "Workflow",
    metadata: {
      workflowCode: workflow.code,
      projectCode: workflow.projectCode,
      name: workflow.name,
      version: workflow.version,
      releaseState: workflow.releaseState,
      createdAt: workflow.createTime,
      updatedAt: workflow.updateTime,
    },
    spec: {
      description: workflow.description ?? "",
      execution: {
        type: workflow.executionType ?? "PARALLEL",
        timeoutMinutes: workflow.timeout ?? 0,
      },
      globalParams: parseJsonValue(workflow.globalParams, []),
      nodes: (dag.taskDefinitionList ?? []).map(buildNode),
      relations: (dag.workflowTaskRelationList ?? []).map((relation) => ({
        source: relation.preTaskCode,
        sourceVersion: relation.preTaskVersion,
        target: relation.postTaskCode,
        targetVersion: relation.postTaskVersion,
      })),
      layout: parseJsonValue(workflow.locations, []),
    },
  };
}
