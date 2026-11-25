import { useEffect, useState } from "react";
import { Lightbulb } from "lucide-react";

import type { AgentActivity } from "../../../../model/types";
import "./agent-activity.css";

type AgentThoughtProps = {
  activity: AgentActivity;
  compact?: boolean;
};

function formatDuration(startedAt?: number, completedAt?: number, now = Date.now()) {
  if (!startedAt) return "";

  const duration = Math.max(0, (completedAt ?? now) - startedAt);
  if (duration < 1000) return "<1s";
  if (duration < 60_000) return Math.round(duration / 1000) + "s";

  const minutes = Math.floor(duration / 60_000);
  const seconds = Math.floor((duration % 60_000) / 1000);
  return minutes + "m " + String(seconds).padStart(2, "0") + "s";
}

function statusLabel(status: AgentActivity["status"]) {
  switch (status) {
    case "error":
      return "执行异常";
    case "denied":
    case "rejected":
      return "已拒绝";
    case "interrupted":
      return "已中断";
    case "awaiting-confirmation":
      return "等待确认";
    case "approved":
      return "已确认";
    case "running":
      return "Thinking";
    case "completed":
      return "Thought";
  }
}

export function AgentThought({ activity, compact = false }: AgentThoughtProps) {
  const isRunning = activity.status === "running";
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!isRunning || !activity.startedAt) return;

    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [activity.startedAt, isRunning]);

  const duration =
    activity.startedAt !== undefined &&
    (isRunning || activity.completedAt !== undefined)
      ? formatDuration(activity.startedAt, activity.completedAt, now)
      : "";

  return (
    <div
      className={
        "agent-thought min-w-0 " +
        (compact ? "agent-thought--compact" : "")
      }
      data-status={activity.status}
    >
      <span
        className="agent-thought__icon"
        data-active={isRunning ? "true" : "false"}
        aria-hidden="true"
      >
        <Lightbulb size={14} strokeWidth={1.6} />
      </span>

      {isRunning ? (
        <span className="agent-activity-shimmer agent-thought__label min-w-0 truncate">
          Thinking
        </span>
      ) : activity.status === "completed" ? (
        <>
          <span className="agent-thought__label">Thought</span>
          {duration ? (
            <span className="agent-thought__duration">for {duration}</span>
          ) : null}
        </>
      ) : (
        <span className="agent-thought__label min-w-0 truncate">
          Thought · {statusLabel(activity.status)}
        </span>
      )}
    </div>
  );
}
