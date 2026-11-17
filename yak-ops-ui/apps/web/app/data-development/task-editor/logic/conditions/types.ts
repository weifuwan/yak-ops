export type ConditionRelation = "AND" | "OR";

export type ConditionTaskStatus =
  | "SUCCESS"
  | "FAILURE"
  | "FORCED_SUCCESS"
  | "KILL"
  | "PAUSE";

export type ConditionTaskOption = {
  code: number;
  name: string;
  taskType: string;
};

export type ConditionDependentItem = {
  depTaskCode: number;
  status: ConditionTaskStatus;
};

export type ConditionDependentGroup = {
  relation: ConditionRelation;
  dependItemList: ConditionDependentItem[];
};

export type ConditionResult = {
  successNode: number[];
  failedNode: number[];
};

export type ConditionsTaskParams = Record<string, unknown> & {
  localParams?: unknown[];
  dependence: {
    relation: ConditionRelation;
    dependTaskList: ConditionDependentGroup[];
  };
  conditionResult: ConditionResult;
};

export type ConditionWorkflowGraph = {
  upstreamTasks: ConditionTaskOption[];
  successTargets: ConditionTaskOption[];
  failedTargets: ConditionTaskOption[];
  unassignedTargetCount: number;
};
