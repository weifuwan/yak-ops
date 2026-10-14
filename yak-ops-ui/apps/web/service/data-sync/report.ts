import type {
  DataSyncExecutionReport,
  DataSyncRealtimeSummary,
  DataSyncStageResult,
} from "./types";

/** Invalid or absent observations never become a successful table or an invented zero. */
export function readExecutionReport(
  content: string | null | undefined,
  tableCount: number,
): DataSyncExecutionReport | undefined {
  if (!content) return undefined;
  try {
    const report = JSON.parse(content);
    if (report?.version === 2) {
      const tables: unknown = report.tables;
      if (!validSummary(report) || !Array.isArray(tables) || tables.length !== tableCount)
        return undefined;
      const indexes = new Set<number>();
      for (const table of tables) {
        if (
          !table ||
          !Number.isInteger(table.mappingIndex) ||
          table.mappingIndex < 0 ||
          table.mappingIndex >= tableCount ||
          indexes.has(table.mappingIndex) ||
          !validStages(table.stages)
        )
          return undefined;
        indexes.add(table.mappingIndex);
      }
      return { ...report, tables };
    }
    if (
      report.version !== 1 ||
      !Number.isFinite(Date.parse(report.observedAt)) ||
      !Array.isArray(report.tables) ||
      report.tables.length !== tableCount
    )
      return undefined;
    const indexes = new Set<number>();
    for (const table of report.tables) {
      if (
        !Number.isInteger(table.mappingIndex) ||
        table.mappingIndex < 0 ||
        table.mappingIndex >= tableCount ||
        indexes.has(table.mappingIndex) ||
        table.step !== "FULL" ||
        table.progressUnit !== "SPLITS" ||
        !["WAITING", "RUNNING", "FINISHED", "FAILED", "CANCELED"].includes(table.status) ||
        ![table.readRows, table.committedRows, table.completedSplits].every(
          (value) => Number.isSafeInteger(value) && value >= 0,
        ) ||
        (table.progressPercent !== null &&
          (!Number.isInteger(table.progressPercent) ||
            table.progressPercent < 0 ||
            table.progressPercent > 100)) ||
        (table.plannedSplits !== null &&
          (!Number.isInteger(table.plannedSplits) || table.plannedSplits < 0))
      )
        return undefined;
      indexes.add(table.mappingIndex);
    }
    return report;
  } catch {
    return undefined;
  }
}

const nullableCount = (value: unknown) =>
  value === null || (Number.isSafeInteger(value) && Number(value) >= 0);

function validStages(stages: unknown): stages is DataSyncStageResult[] {
  if (!Array.isArray(stages) || stages.length !== 2) return false;
  return stages.every(
    (stage, index) =>
      stage &&
      stage.step === ["FULL", "INCREMENTAL"][index] &&
      ["WAITING", "RUNNING", "FINISHED", "FAILED", "CANCELED", "UNKNOWN"].includes(stage.status) &&
      stage.progressUnit === ["SPLITS", "RECORDS"][index] &&
      [stage.readRows, stage.committedRows, stage.plannedSplits, stage.completedSplits].every(
        nullableCount,
      ) &&
      (stage.progressPercent === null ||
        (Number.isInteger(stage.progressPercent) &&
          stage.progressPercent >= 0 &&
          stage.progressPercent <= 100)) &&
      (index === 0 ||
        (stage.progressPercent === null &&
          stage.plannedSplits === null &&
          stage.completedSplits === null &&
          stage.status !== "FINISHED")) &&
      (stage.status !== "FINISHED" || stage.progressPercent === 100) &&
      (stage.failureCode === null || ["READ_FAILED", "WRITE_FAILED"].includes(stage.failureCode)),
  );
}

function validSummary(report: unknown): report is DataSyncRealtimeSummary {
  if (!report || typeof report !== "object") return false;
  const value = report as DataSyncRealtimeSummary;
  return (
    value.version === 2 &&
    Number.isFinite(Date.parse(value.observedAt)) &&
    Number.isSafeInteger(value.generation) &&
    value.generation >= 0 &&
    typeof value.restored === "boolean" &&
    value.counterScope === "GENERATION" &&
    ["INITIALIZING", "SNAPSHOT", "HANDOFF", "STREAMING"].includes(value.phase) &&
    Boolean(value.checkpoints) &&
    nullableCount(value.checkpoints.handoffId) &&
    nullableCount(value.checkpoints.completedId) &&
    validStages(value.stages)
  );
}

/** Reads the compact observation returned by the existing batch Instance endpoint. */
export function readExecutionSummary(
  content: string | null | undefined,
): DataSyncRealtimeSummary | undefined {
  if (!content) return undefined;
  try {
    const report = JSON.parse(content);
    return validSummary(report) ? report : undefined;
  } catch {
    return undefined;
  }
}
