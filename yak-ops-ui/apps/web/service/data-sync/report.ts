import type { DataSyncExecutionReport } from "./types";

/** Invalid or absent observations never become a successful table or an invented zero. */
export function readExecutionReport(
  content: string | null | undefined,
  tableCount: number,
): DataSyncExecutionReport | undefined {
  if (!content) return undefined;
  try {
    const report = JSON.parse(content) as DataSyncExecutionReport;
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
