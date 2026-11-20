import {
  CheckCircle2,
  CircleAlert,
  LoaderCircle,
  ShieldCheck,
  Wrench,
  XCircle,
  type LucideIcon,
} from "lucide-react";

import { TOOL_LABELS } from "../../../model/constants";
import type { AgentToolCall } from "../../../model/types";
import {
  formatToolInput,
  toolStatusLabel,
} from "../../../utils/message-formatters";

type ToolCallCardProps = {
  toolCall: AgentToolCall;
};

export function ToolCallCard({ toolCall }: ToolCallCardProps) {
  const StatusIcon: LucideIcon =
    toolCall.status === "running"
      ? LoaderCircle
      : toolCall.status === "error" || toolCall.status === "interrupted"
        ? CircleAlert
        : toolCall.status === "rejected" || toolCall.status === "denied"
          ? XCircle
          : toolCall.status === "awaiting-confirmation"
            ? ShieldCheck
            : CheckCircle2;

  return (
    <div className="rounded-xl border border-[#e7eae9] bg-[#fbfcfb] px-3 py-2.5">
      <div className="flex min-w-0 items-center gap-2">
        <Wrench size={14} className="shrink-0 text-[#7a8985]" />
        <span className="min-w-0 flex-1 truncate text-sm font-medium text-[#3b4744]">
          {TOOL_LABELS[toolCall.name] ?? toolCall.name}
        </span>
        <span aria-label={toolStatusLabel(toolCall.status)}
          className="flex shrink-0 items-center gap-1 text-xs text-[#8b9693]">
          <StatusIcon
            size={12}
            className={
              toolCall.status === "running"
                ? "animate-spin"
                : toolCall.status === "rejected" || toolCall.status === "denied" || toolCall.status === "error" || toolCall.status === "interrupted"
                  ? "text-[#b96b6b]"
                  : "text-[#6c9b7e]"
            }
          />
          {toolCall.status === "completed" ? null : toolStatusLabel(toolCall.status)}
        </span>
      </div>
      {toolCall.input ? (
        <pre className="mt-2 max-h-36 overflow-auto whitespace-pre-wrap break-all rounded-lg bg-[#f3f5f4] p-2 text-[11px] leading-5 text-[#68736f]">
          {formatToolInput(toolCall.input)}
        </pre>
      ) : null}
    </div>
  );
}
