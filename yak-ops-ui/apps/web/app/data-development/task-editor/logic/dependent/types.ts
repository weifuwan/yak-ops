export type DependentRelation = "AND" | "OR";
export type DependentType = "DEPENDENT_ON_WORKFLOW" | "DEPENDENT_ON_TASK";
export type DependResult = "SUCCESS" | "WAITING" | "FAILED" | "NON_EXEC";
export type DependentFailurePolicy =
  | "DEPENDENT_FAILURE_FAILURE"
  | "DEPENDENT_FAILURE_WAITING";

export type DependentItem = {
  dependentType: DependentType;
  projectCode: number;
  definitionCode: number;
  depTaskCode: number;
  cycle: "hour" | "day" | "week" | "month";
  dateValue: string;
  dependResult: DependResult;
  parameterPassing: boolean;
};

export type DependentGroup = {
  relation: DependentRelation;
  dependItemList: DependentItem[];
};

export type DependentTaskParams = {
  localParams?: unknown[];
  dependence: {
    dependTaskList: DependentGroup[];
    relation: DependentRelation;
    checkInterval: number;
    failurePolicy: DependentFailurePolicy;
    failureWaitingTime: number;
  };
};
