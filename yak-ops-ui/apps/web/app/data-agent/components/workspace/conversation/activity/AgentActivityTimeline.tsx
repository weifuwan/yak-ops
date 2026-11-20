import { useEffect, useState } from "react";
import { Activity, ChevronDown, Clock3, Coins } from "lucide-react";

import type { AgentActivity, AgentReplyEnd, AgentUsage } from "../../../../model/types";
import { AgentActivityItem } from "./AgentActivityItem";

type AgentActivityTimelineProps = {
  activities: AgentActivity[];
  usage?: AgentUsage[];
  replyEnds?: AgentReplyEnd[];
  isStreaming: boolean;
  isError?: boolean;
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

export function AgentActivityTimeline({
  activities,
  usage = [],
  replyEnds = [],
  isStreaming,
  isError = false,
}: AgentActivityTimelineProps) {
  const [expanded, setExpanded] = useState(true);
  useEffect(() => {
    if (isStreaming) setExpanded(true);
  }, [isStreaming]);

  const stepCount = activities.filter((activity) => activity.kind !== "text").length;
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
  const duration = formatDuration(startedAt, isStreaming ? undefined : completedAt);
  const active = isStreaming || activities.some((activity) => activity.status === "running");
  const hasFailure =
    isError ||
    activities.some((activity) =>
      ["error", "denied", "interrupted", "rejected"].includes(activity.status),
    );
  const inputTokens = usage.reduce((total, item) => total + item.inputTokens, 0);
  const outputTokens = usage.reduce((total, item) => total + item.outputTokens, 0);
  const cacheTokens = usage.reduce(
    (total, item) => total + item.cacheInputTokens + item.cacheCreationInputTokens,
    0,
  );

  return (
    <section className="my-2 min-w-0 overflow-hidden rounded-xl border border-[#e7eae9] bg-[#fbfcfb]">
      <button
        type="button"
        aria-expanded={expanded}
        className="flex w-full min-w-0 cursor-pointer items-center gap-2 px-3.5 py-2.5 text-left transition-colors hover:bg-[#f6f8f7]"
        onClick={() => setExpanded((value) => !value)}
      >
        <Activity size={14} className="shrink-0 text-[#77857f]" />
        <span className="text-xs font-medium text-[#3b4744]">执行过程</span>
        <span className="min-w-0 flex-1 truncate text-[11px] text-[#8b9691]">
          {stepCount > 0 ? stepCount + " 个步骤" : "回复生成中"}
        </span>
        {active ? (
          <span className="flex shrink-0 items-center gap-1 text-[10px] text-[#9a7b42]">
            <span className="size-1.5 rounded-full bg-[#d5a34d]" />
            进行中
          </span>
        ) : hasFailure ? (
          <span className="shrink-0 text-[10px] text-[#c45d6c]">存在异常</span>
        ) : (
          <span className="shrink-0 text-[10px] text-[#6b987d]">已完成</span>
        )}
        {duration ? (
          <span className="flex shrink-0 items-center gap-1 text-[10px] tabular-nums text-[#9aa39f]">
            <Clock3 size={11} />
            {duration}
          </span>
        ) : null}
        <ChevronDown
          size={14}
          className={"shrink-0 text-[#89948f] transition-transform duration-200 " + (expanded ? "rotate-180" : "")}
        />
      </button>

      {expanded ? (
        <div className="flex min-w-0 flex-col gap-2 border-t border-[#edf0ef] px-3.5 py-3">
          {activities.map((activity) => (
            <AgentActivityItem
              key={activity.id}
              activity={activity}
              isStreaming={isStreaming}
            />
          ))}
          {usage.length > 0 ? (
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-[#edf0ef] pt-2 text-[10px] text-[#929c97]">
              <span className="flex items-center gap-1">
                <Coins size={11} />
                Token 用量
              </span>
              <span>输入 {inputTokens.toLocaleString()}</span>
              <span>输出 {outputTokens.toLocaleString()}</span>
              {cacheTokens > 0 ? <span>缓存 {cacheTokens.toLocaleString()}</span> : null}
            </div>
          ) : null}
          {replyEnds.some((item) => item.finishedReason !== "completed") ? (
            <div className="border-t border-[#edf0ef] pt-2 text-[10px] text-[#9a7b42]">
              模型回复结束原因：{replyEnds[replyEnds.length - 1]?.finishedReason}
            </div>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
