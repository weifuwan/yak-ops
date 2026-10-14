import type { DataSyncSolution, DataSyncSolutionType, DataSyncStep } from "@/service/data-sync";

export const SOLUTION_LABELS: Record<DataSyncSolutionType, string> = {
  SINGLE_TABLE_OFFLINE: "单表离线",
  DATABASE_OFFLINE: "整库离线",
  SINGLE_TABLE_REALTIME: "单表实时",
};
export const STEP_LABELS: Record<DataSyncStep, string> = {
  SCHEMA: "结构迁移",
  FULL: "全量同步",
  INCREMENTAL: "增量同步",
};

export function findStepCombination(solution: DataSyncSolution | undefined, steps: DataSyncStep[]) {
  if (!solution || new Set(steps).size !== steps.length) return undefined;
  return solution.allowedCombinations.find(
    (combination) =>
      combination.steps.length === steps.length &&
      combination.steps.every((step) => steps.includes(step)),
  );
}

export const solutionSummary = (type: DataSyncSolutionType, steps: DataSyncStep[]) =>
  `${SOLUTION_LABELS[type]} · ${steps
    .filter((step) => step !== "SCHEMA")
    .map((step) => (step === "FULL" ? "全量" : "增量"))
    .join("＋")}`;
