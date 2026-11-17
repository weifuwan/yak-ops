import type {
  ConditionDependentGroup,
  ConditionDependentItem,
  ConditionLocalParam,
  ConditionTaskOption,
  ConditionTaskStatus,
  ConditionWorkflowGraph,
  ConditionsTaskParams,
} from "./types";

type JsonRecord = Record<string, unknown>;

const CONDITION_TASK_STATUSES: ConditionTaskStatus[] = [
  "SUCCESS",
  "FAILURE",
  "FORCED_SUCCESS",
  "KILL",
  "PAUSE",
];

function asRecord(value: unknown): JsonRecord {
  if (typeof value === "string") {
    try {
      const parsed: unknown = JSON.parse(value);
      return parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)
        ? parsed as JsonRecord
        : {};
    } catch {
      return {};
    }
  }
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as JsonRecord
    : {};
}

function normalizeLocalParams(value: unknown): ConditionLocalParam[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => {
    const property = asRecord(item);
    return {
      prop: String(property.prop ?? ""),
      direct: property.direct === "OUT" ? "OUT" : "IN",
      type: String(property.type ?? "VARCHAR"),
      value: property.value === null || property.value === undefined
        ? ""
        : String(property.value),
      sensitive: Boolean(property.sensitive),
    };
  });
}

function normalizeTaskCodes(value: unknown): number[] {
  if (!Array.isArray(value)) return [];
  return [...new Set(value
    .map(Number)
    .filter((code) => Number.isSafeInteger(code) && code > 0))];
}

function normalizeStatus(value: unknown): ConditionTaskStatus {
  return CONDITION_TASK_STATUSES.includes(value as ConditionTaskStatus)
    ? value as ConditionTaskStatus
    : "SUCCESS";
}

export function createConditionDependentItem(taskCode = 0): ConditionDependentItem {
  return {
    depTaskCode: taskCode,
    status: "SUCCESS",
  };
}

export function normalizeConditionsTaskParams(value: unknown): ConditionsTaskParams {
  const params = asRecord(value);
  const dependence = asRecord(params.dependence);
  const conditionResult = asRecord(params.conditionResult);
  const groups = Array.isArray(dependence.dependTaskList)
    ? dependence.dependTaskList
    : [];

  return {
    ...params,
    localParams: normalizeLocalParams(params.localParams),
    dependence: {
      relation: dependence.relation === "OR" ? "OR" : "AND",
      dependTaskList: groups.map((item) => {
        const group = asRecord(item);
        const items = Array.isArray(group.dependItemList) ? group.dependItemList : [];
        return {
          relation: group.relation === "OR" ? "OR" : "AND",
          dependItemList: items.map((condition) => {
            const current = asRecord(condition);
            const depTaskCode = Number(current.depTaskCode ?? 0);
            return {
              depTaskCode: Number.isSafeInteger(depTaskCode) && depTaskCode > 0
                ? depTaskCode
                : 0,
              status: normalizeStatus(current.status),
            };
          }),
        } satisfies ConditionDependentGroup;
      }),
    },
    conditionResult: {
      successNode: normalizeTaskCodes(conditionResult.successNode),
      failedNode: normalizeTaskCodes(conditionResult.failedNode),
    },
  };
}

export function validateConditionsTaskParams(
  value: unknown,
  upstreamTaskCodes: number[],
): string | null {
  const normalized = normalizeConditionsTaskParams(value);
  const groups = normalized.dependence.dependTaskList;
  if (groups.length === 0) return "至少需要配置一个依赖条件组";
  if (groups.some((group) => group.dependItemList.length === 0)) {
    return "每个条件组至少需要配置一个依赖任务";
  }

  const upstreamCodes = new Set(upstreamTaskCodes);
  for (const [groupIndex, group] of groups.entries()) {
    const selectedCodes = new Set<number>();
    for (const item of group.dependItemList) {
      if (!Number.isSafeInteger(item.depTaskCode) || item.depTaskCode <= 0) {
        return "请选择条件组 " + (groupIndex + 1) + " 中的上游任务";
      }
      if (!upstreamCodes.has(item.depTaskCode)) {
        return "条件组 " + (groupIndex + 1) + " 中的依赖任务已不是当前节点的上游任务，请重新选择";
      }
      if (selectedCodes.has(item.depTaskCode)) {
        return "同一个条件组不能重复选择相同的上游任务";
      }
      selectedCodes.add(item.depTaskCode);
    }
  }

  return null;
}

type ConditionGraphNodeLike = {
  id: string;
  data?: {
    taskCode?: number | null;
    name?: string;
    taskType?: string;
    task?: Partial<ConditionTaskOption> | null;
  };
};

type ConditionGraphEdgeLike = {
  source: string;
  target: string;
  sourceHandle?: string | null;
};

function toTaskOption(node: ConditionGraphNodeLike | undefined): ConditionTaskOption | null {
  if (!node) return null;
  const code = Number(node.data?.task?.code ?? node.data?.taskCode);
  if (!Number.isSafeInteger(code) || code <= 0) return null;
  return {
    code,
    name: String(node.data?.task?.name ?? node.data?.name ?? "任务 #" + code),
    taskType: String(node.data?.task?.taskType ?? node.data?.taskType ?? "UNKNOWN"),
  };
}

function uniqueTaskOptions(
  nodeIds: string[],
  nodesById: Map<string, ConditionGraphNodeLike>,
): ConditionTaskOption[] {
  const seen = new Set<number>();
  return nodeIds.flatMap((nodeId) => {
    const option = toTaskOption(nodesById.get(nodeId));
    if (!option || seen.has(option.code)) return [];
    seen.add(option.code);
    return [option];
  });
}

export function getConditionWorkflowGraph(
  nodes: ConditionGraphNodeLike[],
  edges: ConditionGraphEdgeLike[],
  currentNodeId: string,
): ConditionWorkflowGraph {
  const nodesById = new Map(nodes.map((node) => [node.id, node] as const));
  const incoming = edges
    .filter((edge) => edge.target === currentNodeId)
    .map((edge) => edge.source);
  const outgoing = edges.filter((edge) => edge.source === currentNodeId);

  return {
    upstreamTasks: uniqueTaskOptions(incoming, nodesById),
    successTargets: uniqueTaskOptions(
      outgoing
        .filter((edge) => edge.sourceHandle === "success")
        .map((edge) => edge.target),
      nodesById,
    ),
    failedTargets: uniqueTaskOptions(
      outgoing
        .filter((edge) => edge.sourceHandle === "failed")
        .map((edge) => edge.target),
      nodesById,
    ),
    unassignedTargetCount: outgoing.filter(
      (edge) => edge.sourceHandle !== "success" && edge.sourceHandle !== "failed",
    ).length,
  };
}
