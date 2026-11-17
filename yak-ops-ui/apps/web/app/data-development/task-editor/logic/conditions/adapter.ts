import type {
  ConditionDependentGroup,
  ConditionDependentItem,
  ConditionTaskStatus,
  ConditionsTaskParams,
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

export function createConditionDependentItem(
  projectCode: number,
  workflowCode: number,
  taskCode = 0,
): ConditionDependentItem {
  return {
    dependentType: "DEPENDENT_ON_TASK",
    projectCode,
    definitionCode: workflowCode,
    depTaskCode: taskCode,
    cycle: "day",
    dateValue: "today",
    dependResult: "SUCCESS",
    parameterPassing: false,
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
    localParams: Array.isArray(params.localParams) ? params.localParams : [],
    dependence: {
      relation: dependence.relation === "OR" ? "OR" : "AND",
      dependTaskList: groups.map((item) => {
        const group = asRecord(item);
        const items = Array.isArray(group.dependItemList) ? group.dependItemList : [];
        return {
          relation: group.relation === "OR" ? "OR" : "AND",
          dependItemList: items.map((condition) => {
            const current = asRecord(condition);
            const status = String(current.status ?? "SUCCESS") as ConditionTaskStatus;
            return {
              ...createConditionDependentItem(
                Number(current.projectCode ?? 0),
                Number(current.definitionCode ?? 0),
                Number(current.depTaskCode ?? 0),
              ),
              ...current,
              status,
            } as ConditionDependentItem;
          }),
        } as ConditionDependentGroup;
      }),
    },
    conditionResult: {
      conditionSuccess: Boolean(conditionResult.conditionSuccess),
      successNode: Array.isArray(conditionResult.successNode)
        ? conditionResult.successNode.map(Number)
        : [],
      failedNode: Array.isArray(conditionResult.failedNode)
        ? conditionResult.failedNode.map(Number)
        : [],
    },
  };
}
