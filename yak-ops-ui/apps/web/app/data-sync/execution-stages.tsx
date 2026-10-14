import { useEffect, useState } from "react";
import type {
  DataSyncInstanceStatus,
  DataSyncRealtimeSummary,
  DataSyncStageResult,
} from "@/service/data-sync/types";
import { ExecutionProgressBar } from "./execution-progress-bar";

const LABELS = { FULL: "全量同步", INCREMENTAL: "增量同步" };
const STATES: Record<string, DataSyncInstanceStatus> = {
  WAITING: "SUBMITTED",
  RUNNING: "RUNNING",
  FINISHED: "FINISHED",
  FAILED: "FAILED",
  CANCELED: "CANCELED",
  UNKNOWN: "RECONCILING",
};

/** Sampling age is checked even when polling fails and the last response remains on screen. */
export function useObservationStale(status: DataSyncInstanceStatus, observedAt?: string) {
  const [now, setNow] = useState(Date.now());
  const active = ["SUBMITTED", "LAUNCHING", "RUNNING", "CANCELLING", "RECONCILING"].includes(
    status,
  );
  useEffect(() => {
    if (!active) return;
    const timer = window.setInterval(() => setNow(Date.now()), 2000);
    return () => window.clearInterval(timer);
  }, [active]);
  return (
    status === "RECONCILING" || (active && (!observedAt || now - Date.parse(observedAt) > 10000))
  );
}

/** Instance cancellation and unavailable observations never retain a healthy streaming animation. */
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
  if (!stage || stale || stage.status === "UNKNOWN") {
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
  let state = STATES[stage.status];
  if (stage.status !== "FINISHED") {
    if (status === "CANCELLING" || status === "CANCELED") state = status;
    else if (status === "FAILED" && ["WAITING", "RUNNING"].includes(stage.status)) state = "FAILED";
  }
  return (
    <ExecutionProgressBar
      status={state}
      percent={stage.progressPercent}
      realtime={stage.step === "INCREMENTAL"}
      ariaLabel={label}
    />
  );
}

/** The same Instance-scoped phases drive the compact list and expanded execution detail. */
export function ExecutionStages({
  summary,
  status,
  compact = false,
}: {
  summary?: DataSyncRealtimeSummary;
  status: DataSyncInstanceStatus;
  compact?: boolean;
}) {
  const stale = useObservationStale(status, summary?.observedAt);
  let stages: Array<"FULL" | "INCREMENTAL"> = ["FULL", "INCREMENTAL"];
  if (compact && summary) {
    stages = [summary.stages[0].status === "FINISHED" ? "INCREMENTAL" : "FULL"];
  }
  return (
    <div className={compact ? "space-y-1" : "grid gap-4 sm:grid-cols-2"}>
      {stages.map((step) => (
        <div key={step} className="min-w-0">
          <StageProgress
            stage={summary?.stages.find((stage) => stage.step === step)}
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
