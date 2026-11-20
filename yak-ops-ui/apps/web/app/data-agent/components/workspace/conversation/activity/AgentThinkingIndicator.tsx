import { Brain, CheckCircle2, CircleAlert, LoaderCircle, ShieldCheck, XCircle } from "lucide-react";

import type { AgentActivity } from "../../../../model/types";

type AgentThinkingIndicatorProps = {
  activity: AgentActivity;
};

function activityStatusLabel(status: AgentActivity["status"]) {
  switch (status) {
    case "running":
      return "进行中";
    case "completed":
      return "已完成";
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
  }
}

function formatDuration(startedAt?: number, completedAt?: number) {
  if (!startedAt) return "";
  const duration = Math.max(0, (completedAt ?? Date.now()) - startedAt);
  if (duration < 1000) return "不足 1 秒";
  if (duration < 60_000) return Math.round(duration / 1000) + "s";
  const minutes = Math.floor(duration / 60_000);
  const seconds = Math.floor((duration % 60_000) / 1000);
  return minutes + "m " + String(seconds).padStart(2, "0") + "s";
}

export function AgentThinkingIndicator({ activity }: AgentThinkingIndicatorProps) {
  const Icon =
    activity.status === "running"
      ? LoaderCircle
      : activity.status === "error"
        ? CircleAlert
        : activity.status === "denied" || activity.status === "rejected"
          ? XCircle
          : activity.status === "awaiting-confirmation"
            ? ShieldCheck
            : CheckCircle2;
  const duration = formatDuration(activity.startedAt, activity.completedAt);

  return (
    <div className="flex min-w-0 items-center gap-2.5 py-1.5">
      <span className="flex size-8 shrink-0 items-center justify-center rounded-full text-[#000000]">
        {activity.status === "running" ? (
          <Brain size={13} className="animate-pulse" />
        ) : (
          <Brain size={13} />
        )}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-0.5">
          <span className="text-sm font-medium text-[#3b4743]">{activity.title}</span>
          <span
            aria-label={activityStatusLabel(activity.status)}
            className="flex items-center gap-1 text-xs text-[#89948f]"
          >
          <span aria-label={activityStatusLabel(activity.status)} className="flex items-center gap-1 text-xs text-[#000000]">
            <Icon
              size={12}
              className={activity.status === "running" ? "animate-spin" : ""}
            />
            {activity.status === "completed" ? null : activityStatusLabel(activity.status)}
          </span>
          {duration ? (
            <span className="text-xs tabular-nums text-[#a0a9a5]">{duration}</span>
          ) : null}
        </div>
      </div>
    </div>
  );
}
