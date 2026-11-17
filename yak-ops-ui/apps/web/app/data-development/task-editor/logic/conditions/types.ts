export type ConditionRelation = "AND" | "OR";

export type ConditionTaskStatus =
  | "SUCCESS"
  | "FAILURE"
  | "FORCED_SUCCESS"
  | "KILL"
  | "PAUSE";

export type ConditionDependentItem = {
  dependentType: "DEPENDENT_ON_TASK";
  projectCode: number;
  definitionCode: number;
  depTaskCode: number;
  cycle: string;
  dateValue: string;
  dependResult: "SUCCESS";
  parameterPassing: boolean;
  status: ConditionTaskStatus;
};

export type ConditionDependentGroup = {
  relation: ConditionRelation;
  dependItemList: ConditionDependentItem[];
};

export type ConditionsTaskParams = {
  localParams?: unknown[];
  dependence: {
    relation: ConditionRelation;
    dependTaskList: ConditionDependentGroup[];
  };
  conditionResult: {
    conditionSuccess: boolean;
    successNode: number[];
    failedNode: number[];
  };
};
