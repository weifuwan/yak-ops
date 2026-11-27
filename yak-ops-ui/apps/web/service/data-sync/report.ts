import type {
  DataSyncExecutionReport,
  DataSyncExecutionSummary,
  DataSyncMetricsExecutionReport,
  DataSyncMetricsSnapshot,
  DataSyncMetricsTableReport,
  DataSyncStageResult,
  DataSyncStep,
  DataSyncTableMapping,
} from "./types";

/** Formats nullable observations without conflating an unknown value with zero. */
export function formatMetricCount(value: number | null | undefined): string {
  return value == null ? "—" : value.toLocaleString("zh-CN");
}

/** Invalid or absent observations never become a successful table or an invented zero. */
export function readExecutionReport(
  content: string | null | undefined,
  tableCount: number,
  expectedSteps?: DataSyncStep[],
): DataSyncExecutionReport | undefined {
  if (!content) return undefined;
  try {
    const report = JSON.parse(content);
    if (expectedSteps) {
      const observed =
        report?.version === 1
          ? Array.isArray(report.tables)
            ? [...new Set(report.tables.map((table: { step: DataSyncStep }) => table.step))]
            : []
          : report?.stages?.map((stage: DataSyncStageResult) => stage.step);
      if (
        !Array.isArray(observed) ||
        observed.length === 0 ||
        observed.some((step: string) => !expectedSteps.includes(step as DataSyncStep)) ||
        (report?.version === 3 &&
          expectedSteps.includes("SCHEMA") !== observed.includes("SCHEMA")) ||
        (expectedSteps.includes("FULL") &&
          !expectedSteps.includes("INCREMENTAL") &&
          observed.includes("INCREMENTAL"))
      )
        return undefined;
    }
    if (report?.version === 2 || report?.version === 3) {
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
          !(report.version === 3 ? validPreparedStages(table.stages) : validStages(table.stages))
        )
          return undefined;
        if (
          report.version === 3 &&
          (table.stages.length !== report.stages.length ||
            table.stages[0].plannedTables !== 1 ||
            (report.stages[0].status !== "FINISHED" &&
              table.stages
                .slice(1)
                .some(
                  (stage: DataSyncStageResult) =>
                    !["WAITING", "CANCELED", "UNKNOWN"].includes(stage.status),
                )))
        )
          return undefined;
        indexes.add(table.mappingIndex);
      }
      if (
        report.version === 3 &&
        (report.stages[0].plannedTables !== tableCount ||
          report.stages[0].completedTables !==
            tables.filter((table) => table.stages[0].status === "FINISHED").length)
      )
        return undefined;
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
        !["FULL", "INCREMENTAL"].includes(table.step) ||
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
        (stage.plannedSplits === null &&
          stage.completedSplits === null &&
          (stage.status === "FINISHED"
            ? stage.progressPercent === 100
            : stage.progressPercent === null))) &&
      (stage.status !== "FINISHED" || stage.progressPercent === 100) &&
      (stage.failureCode === null || ["READ_FAILED", "WRITE_FAILED"].includes(stage.failureCode)),
  );
}

function validPreparedStages(stages: unknown): stages is DataSyncStageResult[] {
  if (!Array.isArray(stages) || ![2, 3].includes(stages.length)) return false;
  const schema = stages[0];
  if (
    !schema ||
    schema.step !== "SCHEMA" ||
    schema.progressUnit !== "TABLES" ||
    !["WAITING", "RUNNING", "FINISHED", "FAILED", "CANCELED", "UNKNOWN"].includes(schema.status) ||
    !Number.isSafeInteger(schema.plannedTables) ||
    schema.plannedTables < 1 ||
    !Number.isSafeInteger(schema.completedTables) ||
    schema.completedTables < 0 ||
    schema.completedTables > schema.plannedTables ||
    schema.progressPercent !== Math.floor((100 * schema.completedTables) / schema.plannedTables) ||
    (schema.status === "FINISHED") !== (schema.completedTables === schema.plannedTables) ||
    ![null, "PREPARED", "VERIFIED"].includes(schema.action) ||
    (schema.action !== null && schema.status !== "FINISHED") ||
    ![null, "SCHEMA_PREPARATION_FAILED"].includes(schema.failureCode) ||
    [schema.readRows, schema.committedRows, schema.plannedSplits, schema.completedSplits].some(
      (value) => value !== null,
    )
  )
    return false;
  const data = stages.slice(1);
  if (data.length === 2) return validStages(data);
  const full = data[0];
  return (
    full &&
    ["FULL", "INCREMENTAL"].includes(full.step) &&
    full.progressUnit === "SPLITS" &&
    ["WAITING", "RUNNING", "FINISHED", "FAILED", "CANCELED", "UNKNOWN"].includes(full.status) &&
    [full.readRows, full.committedRows, full.plannedSplits, full.completedSplits].every(
      nullableCount,
    ) &&
    (full.progressPercent === null ||
      (Number.isInteger(full.progressPercent) &&
        full.progressPercent >= 0 &&
        full.progressPercent <= 100)) &&
    (full.status !== "FINISHED" || full.progressPercent === 100) &&
    (full.failureCode === null || ["READ_FAILED", "WRITE_FAILED"].includes(full.failureCode))
  );
}

function validSummary(report: unknown): report is DataSyncExecutionSummary {
  if (!report || typeof report !== "object") return false;
  const value = report as DataSyncExecutionSummary;
  return (
    (value.version === 2 || value.version === 3) &&
    Number.isFinite(Date.parse(value.observedAt)) &&
    Number.isSafeInteger(value.generation) &&
    value.generation >= 0 &&
    typeof value.restored === "boolean" &&
    (value.counterScope === "GENERATION" ||
      (value.version === 3 && value.counterScope === "INSTANCE")) &&
    (value.version === 3
      ? ["SCHEMA", "FULL", "INCREMENTAL", "INITIALIZING", "SNAPSHOT", "HANDOFF", "STREAMING"]
      : ["INITIALIZING", "SNAPSHOT", "HANDOFF", "STREAMING"]
    ).includes(value.phase) &&
    (value.version !== 3 ||
      (value.stages?.length === 2
        ? value.counterScope === "INSTANCE" &&
          value.generation === 0 &&
          !value.restored &&
          ["SCHEMA", "FULL", "INCREMENTAL"].includes(value.phase)
        : value.counterScope === "GENERATION" && value.phase !== "FULL")) &&
    Boolean(value.checkpoints) &&
    nullableCount(value.checkpoints.handoffId) &&
    nullableCount(value.checkpoints.completedId) &&
    (value.version === 3 ? validPreparedStages(value.stages) : validStages(value.stages))
  );
}

/** Reads the compact observation returned by the existing batch Instance endpoint. */
export function readExecutionSummary(
  content: string | null | undefined,
): DataSyncExecutionSummary | undefined {
  if (!content) return undefined;
  try {
    const report = JSON.parse(content);
    return validSummary(report) ? report : undefined;
  } catch {
    return undefined;
  }
}


/** Selects the authoritative CDC snapshot without treating a missing FINAL as zero. */
export function selectMetricsSnapshot(
  report?: DataSyncMetricsExecutionReport | null,
): DataSyncMetricsSnapshot | undefined {
  if (!report) return undefined;
  if (report.finalized && report.finalSnapshot) return report.finalSnapshot;
  return report.latestSnapshot ?? undefined;
}

const normalizeMetricTablePath = (value?: string | null) =>
  value?.trim().replace(/["'`]/g, "").toLowerCase() ?? "";

const mappingTablePath = (value: { database?: string; schema?: string; table: string }) =>
  [value.database, value.schema, value.table].filter(Boolean).join(".");

/** Matches a CDC table report to a configured source/sink mapping, preferring the full pair. */
export function findMetricsTableReport(
  reports: readonly DataSyncMetricsTableReport[] | null | undefined,
  mapping: DataSyncTableMapping,
): DataSyncMetricsTableReport | undefined {
  if (!reports?.length) return undefined;
  const source = normalizeMetricTablePath(mappingTablePath(mapping.source));
  const target = normalizeMetricTablePath(mappingTablePath(mapping.target));
  const matches = (actual: string | null, expected: string) =>
    Boolean(expected) && normalizeMetricTablePath(actual) === expected;

  if (source && target) {
    const exact = reports.find(
      (report) => matches(report.sourceTable, source) && matches(report.sinkTable, target),
    );
    if (exact) return exact;
  }
  const sourceMatches = source
    ? reports.filter((report) => matches(report.sourceTable, source))
    : [];
  const targetMatches = target
    ? reports.filter((report) => matches(report.sinkTable, target))
    : [];
  if (sourceMatches.length === 1 && targetMatches.length === 0) return sourceMatches[0];
  if (targetMatches.length === 1 && sourceMatches.length === 0) return targetMatches[0];
  return undefined;
}
