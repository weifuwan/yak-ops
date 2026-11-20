import { Activity, Clock3, Coins } from "lucide-react";

import type { AgentMessage } from "../../../model/types";
import { AgentDetailsTimeline } from "./AgentDetailsTimeline";

type AgentDetailsPanelProps = {
  message: AgentMessage;
  selectedActivityId: string;
};

function formatDuration(startedAt?: number, completedAt?: number) {
  if (startedAt === undefined) return "";
  const duration = Math.max(0, (completedAt ?? Date.now()) - startedAt);
  if (duration < 1000) return "<1s";
  if (duration < 60_000) return Math.round(duration / 1000) + "s";

  const minutes = Math.floor(duration / 60_000);
  const seconds = Math.floor((duration % 60_000) / 1000);
  return minutes + "m " + String(seconds).padStart(2, "0") + "s";
}

function AgentDetailsSummary({ message }: { message: AgentMessage }) {
  const activities = message.activities ?? [];
  const usage = message.usage ?? [];
  const startedAt = activities.reduce<number | undefined>(
    (earliest, activity) =>
      activity.startedAt === undefined
        ? earliest
        : earliest === undefined
          ? activity.startedAt
          : Math.min(earliest, activity.startedAt),
    undefined,
  );
  const completedAt = activities.reduce<number | undefined>(
    (latest, activity) =>
      activity.completedAt === undefined
        ? latest
        : latest === undefined
          ? activity.completedAt
          : Math.max(latest, activity.completedAt),
    undefined,
  );
  const duration = formatDuration(
    startedAt,
    message.status === "streaming" ? undefined : completedAt,
  );
  const stepCount = activities.filter((activity) => activity.kind !== "text").length;
  const inputTokens = usage.reduce((total, item) => total + item.inputTokens, 0);
  const outputTokens = usage.reduce((total, item) => total + item.outputTokens, 0);
  const cacheTokens = usage.reduce(
    (total, item) => total + item.cacheInputTokens + item.cacheCreationInputTokens,
    0,
  );
  const incompleteReply = [...(message.replyEnds ?? [])]
    .reverse()
    .find((item) => item.finishedReason !== "completed");

  return (
    <section className="shrink-0 border-b border-[#ecefed] bg-white px-4 py-3">
      <div className="flex min-w-0 flex-wrap items-center gap-x-4 gap-y-1 pr-2">
        <span className="text-xs font-medium text-[#46504b]">执行概览</span>
        <span className="flex items-center gap-1 text-[11px] text-[#7c8782]">
          <Activity size={12} />
          {stepCount} 个步骤
        </span>
        {duration ? (
          <span className="flex items-center gap-1 text-[11px] tabular-nums text-[#7c8782]">
            <Clock3 size={12} />
            {duration}
          </span>
        ) : null}
      </div>

      {usage.length > 0 ? (
        <div className="mt-2 flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1 pr-2 text-[10px] text-[#929c97]">
          <span className="flex items-center gap-1">
            <Coins size={11} />
            Token 用量
          </span>
          <span>输入 {inputTokens.toLocaleString()}</span>
          <span>输出 {outputTokens.toLocaleString()}</span>
          {cacheTokens > 0 ? <span>缓存 {cacheTokens.toLocaleString()}</span> : null}
        </div>
      ) : null}

      {incompleteReply ? (
        <div className="mt-2 text-[10px] text-[#9a7b42]">
          模型回复结束原因：{incompleteReply.finishedReason}
        </div>
      ) : null}
    </section>
  );
}

export function AgentDetailsPanel({
  message,
  selectedActivityId,
}: AgentDetailsPanelProps) {
  const activities = message.activities ?? [];

  return (
    <div className="relative flex h-full min-h-0 min-w-0 flex-col bg-white">
      <AgentDetailsSummary message={message} />

      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain scrollbar-subtle px-3 pb-6 pt-3">
        {activities.length > 0 ? (
          <AgentDetailsTimeline
            activities={activities}
            selectedActivityId={selectedActivityId}
          />
        ) : (
          <div className="flex h-full items-center justify-center text-xs text-[#9aa39f]">
            暂无可展示的执行步骤
          </div>
        )}

        {message.error ? (
          <div
            role="alert"
            className="ml-9 mt-3 rounded-lg border border-[#f0d6d6] bg-[#fff7f7] px-3 py-2 text-xs leading-5 text-[#a14d4d]"
          >
            {message.error}
          </div>
        ) : null}
      </div>
    </div>
  );
}
