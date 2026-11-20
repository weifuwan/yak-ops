import { useEffect, useState } from "react";
import { ChevronDown, SquareTerminal } from "lucide-react";

import { DataAgentMarkdown } from "../../markdown/DataAgentMarkdown";
import type { AgentActivity } from "../../../model/types";
import { TOOL_LABELS } from "../../../model/constants";
import { formatToolInput } from "../../../utils/message-formatters";

type AgentDetailsTimelineProps = {
  activities: AgentActivity[];
  selectedActivityId: string;
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

function ThoughtEvent({ activity }: { activity: AgentActivity }) {
  const duration = formatDuration(activity.startedAt, activity.completedAt);

  return (
    <div className="relative min-w-0 py-2">
      <span
        aria-hidden="true"
        className="absolute -left-[32px] top-[17px] size-1.5 rounded-full bg-[#c9cfcc]"
      />
      <p className="mb-0 text-[13px] leading-5 text-[#737875]">
        Thought{duration ? " for " + duration : ""}
      </p>
    </div>
  );
}

function CodePanel({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  if (!value) return null;

  return (
    <section className="min-w-0">
      <div className="mb-1.5 text-[11px] font-medium text-[#737875]">
        {label}
      </div>
      <pre className="m-0 max-h-[420px] min-w-0 overflow-auto whitespace-pre-wrap break-words rounded-lg border border-[#e8e9e8] bg-[#fafafa] p-3 font-mono text-[12px] leading-6 text-[#292d2b]">
        {formatToolInput(value)}
      </pre>
    </section>
  );
}

function ActivityDetails({ activity }: { activity: AgentActivity }) {
  const content = activity.content?.trim();

  return (
    <div className="mt-1 min-w-0 border-t border-[#e9eae9] px-3 pb-3 pt-3">
      {content ? (
        <div className="min-w-0 text-[13px] leading-6 text-[#303633] [&_pre]:my-2 [&_pre]:max-h-[420px] [&_pre]:overflow-auto [&_pre]:rounded-lg [&_pre]:border [&_pre]:border-[#e8e9e8] [&_pre]:bg-[#fafafa] [&_pre]:p-3 [&_pre]:font-mono [&_pre]:text-[12px] [&_pre]:leading-6">
          <DataAgentMarkdown content={content} isStreaming={activity.status === "running"} />
        </div>
      ) : null}

      <div className="flex min-w-0 flex-col gap-3">
        <CodePanel label="输入参数" value={activity.input ?? ""} />
        <CodePanel label="执行结果" value={activity.output ?? ""} />
      </div>
    </div>
  );
}

function ExpandableActivity({
  activity,
  expanded,
  onToggle,
}: {
  activity: AgentActivity;
  expanded: boolean;
  onToggle: () => void;
}) {
  const hasDetails = Boolean(
    activity.content?.trim() || activity.input || activity.output,
  );
  const canExpand = activity.kind === "tool" || hasDetails;
  const title =
    activity.kind === "tool"
      ? TOOL_LABELS[activity.toolName ?? ""] ?? activity.toolName ?? activity.title
      : activity.title;

  return (
    <div
      className={
        "relative min-w-0 rounded-[10px] transition-colors duration-200 " +
        (expanded
          ? "border border-[#e4e6e5] bg-white"
          : "border border-transparent")
      }
    >
      <span
        aria-hidden="true"
        className="absolute -left-[36px] top-[12px] flex size-4 items-center justify-center bg-white"
      >
        <SquareTerminal
          size={14}
          strokeWidth={1.6}
          className={
            activity.status === "running"
              ? "text-[#9a875e]"
              : activity.status === "error" ||
                  activity.status === "denied" ||
                  activity.status === "rejected" ||
                  activity.status === "interrupted"
                ? "text-[#b96c72]"
                : "text-[#78827e]"
          }
        />
      </span>

      {canExpand ? (
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls={"agent-details-content-" + activity.id}
          className="flex min-h-9 w-full min-w-0 items-start gap-2 rounded-[9px] px-3 py-2 text-left transition-colors duration-200 hover:bg-[#f8f9f8] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#fe2c55]/20"
          onClick={onToggle}
        >
          <span className="min-w-0 flex-1 break-words text-[14px] leading-6 text-[#252927]">
            {title}
          </span>
          <ChevronDown
            size={15}
            className={
              "mt-1 shrink-0 text-[#747c78] transition-transform duration-200 " +
              (expanded ? "rotate-180" : "")
            }
          />
        </button>
      ) : (
        <div className="flex min-h-9 min-w-0 items-start px-3 py-2">
          <span className="min-w-0 flex-1 break-words text-[14px] leading-6 text-[#252927]">
            {title}
          </span>
        </div>
      )}

      {expanded && canExpand ? (
        <div id={"agent-details-content-" + activity.id}>
          <ActivityDetails activity={activity} />
        </div>
      ) : null}
    </div>
  );
}

export function AgentDetailsTimeline({
  activities,
  selectedActivityId,
}: AgentDetailsTimelineProps) {
  const [expandedActivityId, setExpandedActivityId] = useState<string | null>(
    selectedActivityId || null,
  );

  useEffect(() => {
    if (selectedActivityId) setExpandedActivityId(selectedActivityId);
  }, [selectedActivityId]);

  return (
    <div className="relative min-w-0 pl-9">
      <span
        aria-hidden="true"
        className="pointer-events-none absolute bottom-2 left-[7px] top-2 w-px bg-[#e7e9e8]"
      />

      <div className="flex min-w-0 flex-col gap-1">
        {activities.map((activity) => {
          if (activity.kind === "thinking") {
            return <ThoughtEvent key={activity.id} activity={activity} />;
          }

          if (activity.kind === "text") {
            return (
              <div
                key={activity.id}
                className="relative min-w-0 py-2 text-[14px] leading-7 text-[#27302f]"
              >
                <DataAgentMarkdown
                  content={activity.content ?? ""}
                  isStreaming={activity.status === "running"}
                />
              </div>
            );
          }

          const expanded = expandedActivityId === activity.id;
          return (
            <ExpandableActivity
              key={activity.id}
              activity={activity}
              expanded={expanded}
              onToggle={() =>
                setExpandedActivityId((current) =>
                  current === activity.id ? null : activity.id,
                )
              }
            />
          );
        })}
      </div>
    </div>
  );
}
