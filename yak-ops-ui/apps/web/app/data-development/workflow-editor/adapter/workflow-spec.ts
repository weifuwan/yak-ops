import type {
  WorkflowDagRecord,
  WorkflowTaskRecord,
} from "@/service/workflow/definition";

const SENSITIVE_KEY_PATTERN =
  /password|passwd|secret|token|access.?key|api.?key|private.?key|credential|authorization|cookie/i;

function sanitizeSensitiveValues(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(sanitizeSensitiveValues);
  }

  if (value !== null && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [
        key,
        SENSITIVE_KEY_PATTERN.test(key) && item !== null && item !== undefined
          ? "******"
          : sanitizeSensitiveValues(item),
      ]),
    );
  }

  return value;
}

function parseJsonValue(value: unknown, fallback: unknown): unknown {
  if (value === null || value === undefined || value === "") {
    return fallback;
  }

  if (typeof value !== "string") {
    return sanitizeSensitiveValues(value);
  }

  try {
    return sanitizeSensitiveValues(JSON.parse(value) as unknown);
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
    // The API masks sensitive local/global parameters; this also redacts common credential-like keys.
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
