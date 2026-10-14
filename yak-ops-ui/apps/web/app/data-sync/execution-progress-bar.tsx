import { ChevronRight, Target } from "lucide-react";

import type { DataSyncInstanceStatus } from "@/service/data-sync";

import { getExecutionProgress } from "./execution-progress";
import "./execution-detail.css";

const FLOW_MARKERS = Array.from({ length: 24 }, (_value, index) => index);

/** Identical status visualization in the Task list and the Instance execution detail. */
export function ExecutionProgressBar({
  status,
  percent,
  realtime,
  ariaLabel,
}: {
  status?: DataSyncInstanceStatus;
  percent?: number | null;
  realtime: boolean;
  ariaLabel?: string;
}) {
  const progress = status
    ? getExecutionProgress(status, percent, realtime)
    : { mode: "idle", value: null };
  const statusLabel = status
    ? {
        SUBMITTED: "等待",
        LAUNCHING: "启动中",
        RUNNING: "运行中",
        CANCELLING: "正在停止",
        RECONCILING: "待确认",
        FINISHED: "成功",
        FAILED: "失败",
        CANCELED: "已停止",
      }[status]
    : "暂无执行";
  let progressText = statusLabel;
  if (progress.value !== null) progressText += ` · ${progress.value}%`;
  if (status === "FAILED" && (percent === null || percent === undefined)) {
    progressText += "（未取得进度数据）";
  }
  const label = ariaLabel || (realtime ? "实时同步" : "离线同步");

  return (
    <div
      className="sync-execution-progress"
      data-mode={progress.mode}
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={progress.value ?? undefined}
      aria-valuetext={progressText}
      title={progressText}
    >
      {progress.mode === "running" ? (
        <>
          <Target size={18} className="sync-execution-flow-end" aria-hidden="true" />
          <span className="sync-execution-flow-track" aria-hidden="true">
            {FLOW_MARKERS.map((marker) => (
              <ChevronRight
                key={marker}
                size={18}
                className="sync-execution-flow-arrow"
                style={{ animationDelay: marker * 0.08 + "s" }}
              />
            ))}
          </span>
          <Target size={18} className="sync-execution-flow-end" aria-hidden="true" />
        </>
      ) : (
        <>
          {(progress.mode === "progress" || progress.mode === "failure") &&
          progress.value !== null ? (
            <span
              className="sync-execution-progress-fill"
              style={{ width: `${progress.value}%` }}
              aria-hidden="true"
            />
          ) : null}
          <span
            className="sync-execution-progress-value"
            data-light={progress.mode === "progress" && (progress.value ?? 0) >= 55}
            aria-hidden="true"
          >
            {progress.value === null ? "—" : `${progress.value}%`}
          </span>
        </>
      )}
    </div>
  );
}
