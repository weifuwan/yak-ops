import {
  CheckCircle2,
  ChevronRight,
  CircleAlert,
  LoaderCircle,
  ShieldCheck,
  Wrench,
  XCircle,
} from "lucide-react";

import { TOOL_LABELS } from "../../../../model/constants";
import type { AgentActivity } from "../../../../model/types";
import { toolStatusLabel } from "../../../../utils/message-formatters";

type AgentToolActivityProps = {
  activity: AgentActivity;
  selected?: boolean;
  onSelect?: (activityId: string) => void;
};

function formatDuration(startedAt?: number, completedAt?: number) {
  if (!startedAt) return "";
  const duration = Math.max(0, (completedAt ?? Date.now()) - startedAt);
  if (duration < 1000) return "<1s";
  if (duration < 60_000) return Math.round(duration / 1000) + "s";
  const minutes = Math.floor(duration / 60_000);
  const seconds = Math.floor((duration % 60_000) / 1000);
  return minutes + "m " + String(seconds).padStart(2, "0") + "s";
}

export function AgentToolActivity({
  activity,
  selected = false,
  onSelect,
}: AgentToolActivityProps) {
  const status = activity.status;
  const StatusIcon =
    status === "running"
      ? LoaderCircle
      : status === "error" || status === "interrupted"
        ? CircleAlert
        : status === "denied" || status === "rejected"
          ? XCircle
          : status === "awaiting-confirmation"
            ? ShieldCheck
            : CheckCircle2;
  const duration = formatDuration(
    activity.startedAt,
    status === "running" ? undefined : activity.completedAt,
  );
  const toolLabel =
    TOOL_LABELS[activity.toolName ?? ""] ?? activity.toolName ?? "工具调用";

  return (
    <button
      type="button"
      aria-label={"查看 " + toolLabel + " 执行详情"}
      aria-expanded={selected}
      aria-controls="agent-details-panel"
      className={
        "group flex min-h-9 w-full min-w-0 items-center gap-2 rounded-lg px-2.5 py-2 text-left transition-[background-color,color] duration-200 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25 " +
        (selected
          ? "bg-[#f1f4f2] text-[#20201e]"
          : "bg-transparent text-[#20201e] hover:bg-[#e5e5e5]")
      }
      onClick={() => onSelect?.(activity.id)}
    >
      <Wrench size={13} className="shrink-0 text-[#7a8985]" />
      <span className="min-w-0 flex-1 truncate text-[13px] font-medium text-[#20201e]">
        {toolLabel}
      </span>
      <span
        className={
          "flex shrink-0 items-center gap-1 text-[10px] " +
          (status === "error" ||
          status === "denied" ||
          status === "rejected" ||
          status === "interrupted"
            ? "text-[#c45d70]"
            : status === "running" || status === "awaiting-confirmation"
              ? "text-[#9a7b42]"
              : "text-[#7b9184]")
        }
      >
        <StatusIcon
          size={12}
          className={status === "running" ? "animate-spin" : ""}
        />
        {toolStatusLabel(status)}
      </span>
      {duration ? (
        <span className="shrink-0 text-[10px] tabular-nums text-[#9aa39f]">
          {duration}
        </span>
      ) : null}
      <ChevronRight
        size={14}
        className={
          "shrink-0 text-[#7e8985] transition-[opacity,transform] duration-200 ease-out " +
          (selected
            ? "translate-x-0 opacity-100"
            : "translate-x-[-3px] opacity-0 group-hover:translate-x-0 group-hover:opacity-100 group-focus-visible:translate-x-0 group-focus-visible:opacity-100")
        }
      />
    </button>
  );
}
