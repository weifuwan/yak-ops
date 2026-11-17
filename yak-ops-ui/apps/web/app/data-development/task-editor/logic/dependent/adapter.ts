import type {
  DependResult,
  DependentFailurePolicy,
  DependentGroup,
  DependentItem,
  DependentRelation,
  DependentTaskParams,
} from "./types";

type JsonRecord = Record<string, unknown>;

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

export function createDependentItem(
  projectCode: number,
  workflowCode = 0,
): DependentItem {
  return {
    dependentType: "DEPENDENT_ON_TASK",
    projectCode,
    definitionCode: workflowCode,
    depTaskCode: 0,
    cycle: "day",
    dateValue: "today",
    dependResult: "SUCCESS",
    parameterPassing: false,
  };
}

export function normalizeDependentTaskParams(value: unknown): DependentTaskParams {
  const params = asRecord(value);
  const dependence = asRecord(params.dependence);
  const groups = Array.isArray(dependence.dependTaskList)
    ? dependence.dependTaskList
    : [];

  return {
    ...params,
    localParams: Array.isArray(params.localParams) ? params.localParams : [],
    dependence: {
      relation: dependence.relation === "OR" ? "OR" : "AND",
      dependTaskList: groups.map((item) => {
        const group = asRecord(item);
        const items = Array.isArray(group.dependItemList) ? group.dependItemList : [];
        return {
          relation: group.relation === "OR" ? "OR" : "AND",
          dependItemList: items.map((itemValue) => {
            const item = asRecord(itemValue);
            const dependentType = item.dependentType === "DEPENDENT_ON_WORKFLOW"
              ? "DEPENDENT_ON_WORKFLOW"
              : "DEPENDENT_ON_TASK";
            const cycle = ["hour", "day", "week", "month"].includes(String(item.cycle))
              ? String(item.cycle) as DependentItem["cycle"]
              : "day";
            const dependResult = ["SUCCESS", "WAITING", "FAILED", "NON_EXEC"].includes(String(item.dependResult))
              ? String(item.dependResult) as DependResult
              : "SUCCESS";
            return {
              ...createDependentItem(
                Number(item.projectCode ?? 0),
                Number(item.definitionCode ?? 0),
              ),
              ...item,
              dependentType,
              projectCode: Number(item.projectCode ?? 0),
              definitionCode: Number(item.definitionCode ?? 0),
              depTaskCode: Number(item.depTaskCode ?? 0),
              cycle,
              dateValue: String(item.dateValue ?? "today"),
              dependResult,
              parameterPassing: Boolean(item.parameterPassing),
            } as DependentItem;
          }),
        } as DependentGroup;
      }),
      checkInterval: Math.max(1, Number(dependence.checkInterval ?? 60) || 60),
      failurePolicy: dependence.failurePolicy === "DEPENDENT_FAILURE_WAITING"
        ? "DEPENDENT_FAILURE_WAITING"
        : "DEPENDENT_FAILURE_FAILURE",
      failureWaitingTime: Math.max(0, Number(dependence.failureWaitingTime ?? 30) || 0),
    },
  };
}

export function validateDependentTaskParams(value: unknown): string | null {
  const normalized = normalizeDependentTaskParams(value);
  const groups = normalized.dependence.dependTaskList;
  if (groups.length === 0) return "至少需要配置一个依赖条件组";
  if (groups.some((group) => group.dependItemList.length === 0)) {
    return "每个依赖条件组至少需要配置一个依赖对象";
  }
  for (const group of groups) {
    for (const item of group.dependItemList) {
      if (item.projectCode <= 0 || item.definitionCode <= 0) {
        return "请为每个依赖对象选择所属工作流";
      }
      if (item.dependentType === "DEPENDENT_ON_WORKFLOW" && item.depTaskCode !== 0) {
        return "工作流依赖的 depTaskCode 必须为 0";
      }
      if (item.dependentType === "DEPENDENT_ON_TASK" && item.depTaskCode === 0) {
        return "请选择依赖任务或该工作流的全部任务";
      }
    }
  }
  if (normalized.dependence.checkInterval <= 0) return "检查间隔必须大于 0 秒";
  if (
    normalized.dependence.failurePolicy === "DEPENDENT_FAILURE_WAITING" &&
    normalized.dependence.failureWaitingTime <= 0
  ) {
    return "失败等待时间必须大于 0 分钟";
  }
  return null;
}
