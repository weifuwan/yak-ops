import {
  CheckCircle2,
  CircleAlert,
  LoaderCircle,
  ShieldCheck,
  Wrench,
  XCircle,
} from "lucide-react";

import { TOOL_LABELS } from "../../../../model/constants";
import type { AgentActivity } from "../../../../model/types";
import { toolStatusLabel } from "../../../../utils/message-formatters";
import { AgentActivityDetails } from "./AgentActivityDetails";

type AgentToolActivityProps = {
  activity: AgentActivity;
};

export function AgentToolActivity({ activity }: AgentToolActivityProps) {
  const status = activity.status;
  const StatusIcon =
    status === "running"
      ? LoaderCircle
      : status === "error"
        ? CircleAlert
        : status === "denied" || status === "rejected"
          ? XCircle
          : status === "awaiting-confirmation"
            ? ShieldCheck
            : CheckCircle2;
  const duration =
    activity.startedAt && activity.completedAt
      ? Math.max(0, activity.completedAt - activity.startedAt)
      : null;

  return (
    <div className="min-w-0 rounded-xl border border-[#e7eae9] bg-white px-3 py-2.5">
      <div className="flex min-w-0 items-center gap-2">
        <Wrench size={13} className="shrink-0 text-[#7a8985]" />
        <span className="min-w-0 flex-1 truncate text-xs font-medium text-[#3b4744]">
          {TOOL_LABELS[activity.toolName ?? ""] ?? activity.toolName ?? "工具调用"}
        </span>
        <span className="flex shrink-0 items-center gap-1 text-[10px] text-[#89948f]">
          <StatusIcon
            size={12}
            className={
              status === "running"
                ? "animate-spin"
                : status === "error" || status === "denied" || status === "rejected"
                  ? "text-[#d45c70]"
                  : "text-[#5d9b78]"
            }
          />
          {toolStatusLabel(status)}
        </span>
        {duration !== null ? (
          <span className="shrink-0 text-[10px] tabular-nums text-[#a0a9a5]">
            {duration < 1000 ? "<1s" : Math.round(duration / 1000) + "s"}
          </span>
        ) : null}
      </div>
      <AgentActivityDetails input={activity.input} output={activity.output} />
    </div>
  );
}
