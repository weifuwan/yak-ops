import { useEffect, useState } from "react";
import type {
  DataSyncBatchReport,
  DataSyncExecutionSummary,
  DataSyncInstanceStatus,
  DataSyncStageResult,
  DataSyncStep,
  DataSyncTableResult,
} from "@/service/data-sync/types";
import { ExecutionProgressBar } from "./execution-progress-bar";

const LABELS = { SCHEMA: "结构迁移", FULL: "全量同步", INCREMENTAL: "增量同步" };
const STATES: Record<string, DataSyncInstanceStatus> = {
  WAITING: "SUBMITTED_SUCCESS",
  RUNNING: "RUNNING_EXECUTION",
  FINISHED: "SUCCESS",
  FAILED: "FAILURE",
  CANCELED: "KILL",
  UNKNOWN: "NEED_FAULT_TOLERANCE",
};

type StageStatus = DataSyncStageResult["status"];

function emptyStage(
  step: DataSyncStep,
  status: StageStatus,
  progressPercent: number | null = null,
): DataSyncStageResult {
  return {
    step,
    status,
    progressPercent,
    progressUnit: step === "SCHEMA" ? "TABLES" : step === "INCREMENTAL" ? "RECORDS" : "SPLITS",
    readRows: null,
    committedRows: null,
    plannedSplits: null,
    completedSplits: null,
    failureCode: null,
    ...(step === "SCHEMA" ? { plannedTables: 1, completedTables: status === "FINISHED" ? 1 : 0 } : {}),
  };
}

function completedStage(step: DataSyncStep, observed?: DataSyncStageResult): DataSyncStageResult {
  return {
    ...emptyStage(step, "FINISHED", 100),
    ...observed,
    step,
    status: "FINISHED",
    progressPercent: 100,
  };
}

function phaseTarget(
  phase: DataSyncExecutionSummary["phase"],
): { step: DataSyncStep; status: "RUNNING" | "WAITING" } | undefined {
  switch (phase) {
    case "INITIALIZING":
    case "SCHEMA":
      return { step: "SCHEMA", status: "RUNNING" };
    case "FULL":
    case "SNAPSHOT":
      return { step: "FULL", status: "RUNNING" };
    case "INCREMENTAL":
    case "STREAMING":
      return { step: "INCREMENTAL", status: "RUNNING" };
    case "HANDOFF":
      return { step: "INCREMENTAL", status: "RUNNING" };
    default:
      return undefined;
  }
}

/** The reported phase is authoritative: all earlier phases have already completed. */
function resolveSummaryStages(
  summary: DataSyncExecutionSummary,
  steps: DataSyncStep[],
): DataSyncStageResult[] {
  const target = phaseTarget(summary.phase);
  if (!target) return summary.stages;
  const activeIndex = steps.indexOf(target.step);
  if (activeIndex < 0) return summary.stages;

  const observedByStep = new Map(summary.stages.map((stage) => [stage.step, stage] as const));
  return steps.map((step, index) => {
    const observed = observedByStep.get(step);
    if (index < activeIndex) {
      if (observed?.status === "FAILED" || observed?.status === "CANCELED") return observed;
      return completedStage(step, observed);
    }
    if (index === activeIndex) {
      if (observed?.status === "FAILED" || observed?.status === "CANCELED") return observed;
      if (observed?.status === "FINISHED") return completedStage(step, observed);
      return {
        ...emptyStage(step, target.status),
        ...observed,
        step,
        status: target.status,
        progressPercent: target.status === "WAITING" ? 0 : observed?.progressPercent ?? null,
      };
    }
    return {
      ...emptyStage(step, "WAITING", 0),
      ...observed,
      step,
      status: "WAITING",
      progressPercent: 0,
    };
  });
}

function aggregateStatus(tables: DataSyncTableResult[]): StageStatus {
  if (tables.some((table) => table.status === "FAILED")) return "FAILED";
  if (tables.some((table) => table.status === "CANCELED")) return "CANCELED";
  if (tables.length > 0 && tables.every((table) => table.status === "FINISHED")) return "FINISHED";
  if (tables.some((table) => table.status === "RUNNING")) return "RUNNING";
  return "WAITING";
}

function aggregateProgress(tables: DataSyncTableResult[], status: StageStatus): number | null {
  if (status === "FINISHED") return 100;
  const hasCompletePlan = tables.length > 0 && tables.every((table) => table.plannedSplits !== null);
  const planned = tables.reduce((sum, table) => sum + (table.plannedSplits ?? 0), 0);
  const completed = tables.reduce((sum, table) => sum + table.completedSplits, 0);
  if (hasCompletePlan && planned > 0) return Math.min(99, Math.floor((completed * 100) / planned));
  const percentages = tables
    .map((table) => table.progressPercent)
    .filter((value): value is number => value !== null);
  return percentages.length
    ? Math.min(99, Math.floor(percentages.reduce((sum, value) => sum + value, 0) / percentages.length))
    : null;
}

function resolveBatchStages(report: DataSyncBatchReport, steps: DataSyncStep[]): DataSyncStageResult[] {
  const tables = report.tables;
  const fullComplete =
    tables.length > 0 &&
    tables.every((table) => table.step === "INCREMENTAL" || table.status === "FINISHED");
  const fullTables = tables.filter((table) => table.step === "FULL");
  const incrementalTables = tables.filter((table) => table.step === "INCREMENTAL");
  const stages: DataSyncStageResult[] = [];

  if (steps.includes("SCHEMA") && tables.length > 0) {
    stages.push({
      ...emptyStage("SCHEMA", "FINISHED", 100),
      plannedTables: tables.length,
      completedTables: tables.length,
    });
  }
  if (steps.includes("FULL")) {
    const status: StageStatus = fullComplete ? "FINISHED" : aggregateStatus(fullTables);
    stages.push({
      ...emptyStage("FULL", status, aggregateProgress(fullTables, status)),
      readRows: fullTables.reduce((sum, table) => sum + table.readRows, 0),
      committedRows: fullTables.reduce((sum, table) => sum + table.committedRows, 0),
      plannedSplits: fullTables.some((table) => table.plannedSplits !== null)
        ? fullTables.reduce((sum, table) => sum + (table.plannedSplits ?? 0), 0)
        : null,
      completedSplits: fullTables.reduce((sum, table) => sum + table.completedSplits, 0),
      failureCode: fullTables.find((table) => table.failureCode)?.failureCode ?? null,
    });
  }
  if (steps.includes("INCREMENTAL")) {
    const status: StageStatus =
      !fullComplete || incrementalTables.length === 0 ? "WAITING" : aggregateStatus(incrementalTables);
    stages.push({
      ...emptyStage("INCREMENTAL", status, status === "FINISHED" ? 100 : null),
      readRows: incrementalTables.reduce((sum, table) => sum + table.readRows, 0),
      committedRows: incrementalTables.reduce((sum, table) => sum + table.committedRows, 0),
      failureCode: incrementalTables.find((table) => table.failureCode)?.failureCode ?? null,
    });
  }
  return stages;
}

/** Sampling age is checked even when polling fails and the last response remains on screen. */
export function useObservationStale(status: DataSyncInstanceStatus, observedAt?: string) {
  const [now, setNow] = useState(Date.now());
  const active = ["SUBMITTED_SUCCESS", "DISPATCH", "RUNNING_EXECUTION", "DELAY_EXECUTION", "NEED_FAULT_TOLERANCE"].includes(
    status,
  );
  useEffect(() => {
    if (!active) return;
    const timer = window.setInterval(() => setNow(Date.now()), 2000);
    return () => window.clearInterval(timer);
  }, [active]);
  return (
    status === "NEED_FAULT_TOLERANCE" || (active && (!observedAt || now - Date.parse(observedAt) > 10000))
  );
}

export function StageProgress({
  stage,
  status,
  stale,
  label,
}: {
  stage?: DataSyncStageResult;
  status: DataSyncInstanceStatus;
  stale: boolean;
  label: string;
}) {
  if (status === "SUCCESS" || status === "FORCED_SUCCESS") {
    return <ExecutionProgressBar status={status} realtime={false} ariaLabel={label} />;
  }
  if (!stage || stage.status === "UNKNOWN") {
    return (
      <div
        className="sync-execution-progress"
        data-mode="idle"
        role="progressbar"
        aria-label={label}
        aria-valuetext={stale ? "观测已过期" : "未取得阶段观测"}
        title={stale ? "观测已过期" : "未取得阶段观测"}
      >
        <span>—</span>
      </div>
    );
  }
  if (stage.status === "FINISHED") {
    return <ExecutionProgressBar status="SUCCESS" percent={100} realtime={false} ariaLabel={label} />;
  }
  if (stage.status === "FAILED") {
    return <ExecutionProgressBar status="FAILURE" percent={stage.progressPercent} realtime={false} ariaLabel={label} />;
  }
  if (stage.status === "CANCELED") {
    return <ExecutionProgressBar status="KILL" percent={stage.progressPercent} realtime={false} ariaLabel={label} />;
  }
  if (stale) {
    return (
      <div
        className="sync-execution-progress"
        data-mode="idle"
        role="progressbar"
        aria-label={label}
        aria-valuetext="观测已过期"
        title="观测已过期"
      >
        <span>—</span>
      </div>
    );
  }
  let state = STATES[stage.status];
  if (status === "KILL") state = status;
  else if (status === "FAILURE" && ["WAITING", "RUNNING"].includes(stage.status)) state = "FAILURE";
  return (
    <ExecutionProgressBar
      status={state}
      percent={stage.progressPercent}
      realtime={stage.step === "INCREMENTAL" && stage.progressUnit === "RECORDS"}
      ariaLabel={label}
    />
  );
}

/** The same Instance-scoped phases drive the compact list and expanded execution detail. */
export function ExecutionStages({
  summary,
  batchReport,
  status,
  compact = false,
  steps,
}: {
  summary?: DataSyncExecutionSummary;
  batchReport?: DataSyncBatchReport;
  status: DataSyncInstanceStatus;
  compact?: boolean;
  steps?: DataSyncStep[];
}) {
  const stageSteps: DataSyncStep[] =
    steps ??
    summary?.stages.map((stage) => stage.step) ??
    ["FULL", "INCREMENTAL"];
  const observedAt = summary?.observedAt ?? batchReport?.observedAt;
  const stale = useObservationStale(status, observedAt);
  const resolvedStages = summary
    ? resolveSummaryStages(summary, stageSteps)
    : batchReport
      ? resolveBatchStages(batchReport, stageSteps)
      : undefined;
  let visibleSteps = stageSteps;
  if (compact) {
    const active =
      resolvedStages?.find((stage) => stage.status === "RUNNING") ??
      resolvedStages?.find((stage) => stage.status !== "FINISHED");
    visibleSteps = [active?.step || stageSteps[stageSteps.length - 1]];
  }
  return (
    <div className={compact ? "space-y-1" : "grid gap-4 sm:grid-cols-2 xl:grid-cols-3"}>
      {visibleSteps.map((step) => (
        <div key={step} className="min-w-0">
          <StageProgress
            stage={resolvedStages?.find((stage) => stage.step === step)}
            status={status}
            stale={stale}
            label={LABELS[step]}
          />
          <div className={compact ? "mt-1 text-xs" : "mt-2 text-sm font-medium"}>
            {LABELS[step]}
          </div>
        </div>
      ))}
    </div>
  );
}
