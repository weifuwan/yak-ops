import type { DataSyncSolution, DataSyncProductType, DataSyncStep } from "@/service/data-sync";

export const PRODUCT_LABELS: Record<DataSyncProductType, string> = {
  SINGLE_TABLE_BATCH: "单表离线",
  DATABASE_REPLICATION: "整库实时",
};
export const STEP_LABELS: Record<DataSyncStep, string> = {
  SCHEMA: "准备目标表结构",
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

export function strategyLabel(type: DataSyncProductType, steps: DataSyncStep[]): string {
  if (type === "DATABASE_REPLICATION") {
    if (!steps.includes("INCREMENTAL")) return "全量后结束";
    return steps.includes("FULL") ? "全量后持续增量" : "仅增量";
  }
  if (!steps.includes("INCREMENTAL")) return "全量同步";
  return steps.includes("FULL") ? "首次全量，后续按字段增量" : "按字段增量";
}

export const syncSummary = (type: DataSyncProductType, steps: DataSyncStep[]) =>
  `${PRODUCT_LABELS[type]} · ${strategyLabel(type, steps)}`;
