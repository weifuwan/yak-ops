import {
  Activity,
  Brain,
  CheckCircle2,
  CircleAlert,
  Clock3,
  Coins,
  LoaderCircle,
  ShieldCheck,
  Wrench,
  XCircle,
} from "lucide-react";

import { TOOL_LABELS } from "../../../model/constants";
import type { AgentActivity, AgentMessage } from "../../../model/types";
import { toolStatusLabel } from "../../../utils/message-formatters";
import { AgentActivityDetails } from "./activity/AgentActivityDetails";

type AgentDetailsPanelProps = {
  message: AgentMessage;
  selectedActivityId: string;
  onClose: () => void;
};

function formatDuration(startedAt?: number, completedAt?: number) {
  if (!startedAt) return "";
  const duration = Math.max(0, (completedAt ?? Date.now()) - startedAt);
  if (duration < 1000) return "不足 1 秒";
  if (duration < 60_000) return Math.round(duration / 1000) + "s";
  const minutes = Math.floor(duration / 60_000);
  const seconds = Math.floor((duration % 60_000) / 1000);
  return minutes + "m " + String(seconds).padStart(2, "0") + "s";
}

function StatusIcon({ activity }: { activity: AgentActivity }) {
  const Icon =
    activity.status === "running"
      ? LoaderCircle
      : activity.status === "error" || activity.status === "interrupted"
        ? CircleAlert
        : activity.status === "denied" || activity.status === "rejected"
          ? XCircle
          : activity.status === "awaiting-confirmation"
            ? ShieldCheck
            : CheckCircle2;

  return (
    <Icon
      size={12}
      className={
        activity.status === "running"
          ? "animate-spin text-[#9a7b42]"
          : activity.status === "error" ||
              activity.status === "denied" ||
              activity.status === "rejected" ||
              activity.status === "interrupted"
            ? "text-[#c45d70]"
            : "text-[#6b987d]"
      }
    />
  );
}

function ActivityHeading({ activity }: { activity: AgentActivity }) {
  const duration = formatDuration(
    activity.startedAt,
    activity.status === "running" ? undefined : activity.completedAt,
  );

  if (activity.kind === "tool") {
    const label =
      TOOL_LABELS[activity.toolName ?? ""] ?? activity.toolName ?? "工具调用";
    return (
      <div className="flex min-w-0 items-center gap-2">
        <Wrench size={13} className="shrink-0 text-[#7a8985]" />
        <span className="min-w-0 flex-1 truncate text-[13px] font-medium text-[#20201e]">
          {label}
        </span>
        <span className="flex shrink-0 items-center gap-1 text-[10px] text-[#89948f]">
          <StatusIcon activity={activity} />
          {toolStatusLabel(activity.status)}
        </span>
        {duration ? (
          <span className="shrink-0 text-[10px] tabular-nums text-[#a0a9a5]">
            {duration}
          </span>
        ) : null}
      </div>
    );
  }

  const isThinking = activity.kind === "thinking";
  return (
    <div className="flex min-w-0 items-start gap-2.5">
      <span className="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full bg-[#f0f2f1] text-[#6d7b76]">
        {isThinking ? <Brain size={13} /> : <Activity size={13} />}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-0.5">
          <span className="text-[11px] font-medium text-[#7e8985]">
            {isThinking ? "Thought" : "Progress"}
            {duration ? " for " + duration : ""}
          </span>
          <span className="flex items-center gap-1 text-[10px] text-[#89948f]">
            <StatusIcon activity={activity} />
            {toolStatusLabel(activity.status)}
          </span>
        </div>
        <p className="mb-0 mt-1 text-[13px] leading-6 text-[#20201e]">
          {activity.title}
        </p>
      </div>
    </div>
  );
}

export function AgentDetailsPanel({
  message,
  selectedActivityId,
  onClose,
}: AgentDetailsPanelProps) {
  const activities = (message.activities ?? []).filter(
    (activity) => activity.kind !== "text",
  );
  const usage = message.usage ?? [];
  const inputTokens = usage.reduce((total, item) => total + item.inputTokens, 0);
  const outputTokens = usage.reduce((total, item) => total + item.outputTokens, 0);
  const cacheTokens = usage.reduce(
    (total, item) => total + item.cacheInputTokens + item.cacheCreationInputTokens,
    0,
  );

  return (
    <div className="flex h-full min-h-0 min-w-0 flex-col">
      <header className="flex h-[58px] shrink-0 items-center justify-between gap-3 border-b border-[#ecefed] px-4">
        <div className="min-w-0">
          <h2 className="mb-0 text-[14px] font-semibold text-[#20201e]">Details</h2>
          <p className="mb-0 mt-0.5 text-[10px] text-[#929c97]">
            Agent 执行过程 · {activities.length} 个步骤
          </p>
        </div>
        <button
          type="button"
          aria-label="关闭执行详情"
          title="关闭"
          className="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-md text-[#87928f] transition-colors hover:bg-[#f2f4f3] hover:text-[#303936] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25"
          onClick={onClose}
        >
          <X size={15} />
        </button>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain scrollbar-subtle px-4 py-3">
        {activities.length > 0 ? (
          <div className="flex min-w-0 flex-col gap-2">
            {activities.map((activity) => {
              const selected = activity.id === selectedActivityId;
              const isTool = activity.kind === "tool";
              return (
                <section
                  key={activity.id}
                  id={"agent-detail-" + activity.id}
                  className={
                    "min-w-0 rounded-xl transition-colors duration-200 " +
                    (isTool
                      ? "border px-3 py-3 "
                      : "px-2.5 py-2.5 ") +
                    (selected
                      ? "border-[#dce5df] bg-[#f6f9f7]"
                      : isTool
                        ? "border-[#edf0ee] bg-white"
                        : "border border-transparent")
                  }
                >
                  <ActivityHeading activity={activity} />
                  {isTool ? (
                    <AgentActivityDetails
                      input={activity.input}
                      output={activity.output}
                      defaultOpen={selected}
                    />
                  ) : null}
                </section>
              );
            })}
          </div>
        ) : (
          <div className="flex h-full items-center justify-center text-xs text-[#9aa39f]">
            暂无可展示的执行步骤
          </div>
        )}

        {message.error ? (
          <div
            role="alert"
            className="mt-3 rounded-lg border border-[#f0d6d6] bg-[#fff7f7] px-3 py-2 text-xs leading-5 text-[#a14d4d]"
          >
            {message.error}
          </div>
        ) : null}
      </div>

      {usage.length > 0 ? (
        <footer className="flex shrink-0 flex-wrap items-center gap-x-3 gap-y-1 border-t border-[#ecefed] px-4 py-3 text-[10px] text-[#929c97]">
          <span className="flex items-center gap-1">
            <Coins size={11} />
            Token 用量
          </span>
          <span>输入 {inputTokens.toLocaleString()}</span>
          <span>输出 {outputTokens.toLocaleString()}</span>
          {cacheTokens > 0 ? <span>缓存 {cacheTokens.toLocaleString()}</span> : null}
        </footer>
      ) : null}
    </div>
  );
}
