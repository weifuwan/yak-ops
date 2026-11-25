import { useEffect, useState } from "react";
import {
  Activity,
  CheckCircle2,
  ChevronDown,
  CircleAlert,
  LoaderCircle,
  Pencil,
  Play,
  Search,
  ShieldCheck,
} from "lucide-react";

import type { AgentActivity } from "../../../../model/types";
import { AgentActivityItem } from "./AgentActivityItem";
import type {
  AgentActivityGroup as AgentActivityGroupModel,
  ActivityGroupCategory,
} from "./activity-presenter";

type AgentActivityGroupProps = {
  group: AgentActivityGroupModel;
  expanded: boolean;
  onToggle: () => void;
  isStreaming: boolean;
  selectedActivityId?: string | null;
  onSelectActivity?: (activityId: string) => void;
};

const GROUP_LABELS: Record<ActivityGroupCategory, string> = {
  exploring: "Exploring",
  changed: "Changed",
  ran: "Ran",
  other: "Activity",
};

const GROUP_ICONS = {
  exploring: Search,
  changed: Pencil,
  ran: Play,
  other: Activity,
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

export function AgentActivityGroup({
  group,
  expanded,
  onToggle,
  isStreaming,
  selectedActivityId,
  onSelectActivity,
}: AgentActivityGroupProps) {
  const hasRunning = group.activities.some(
    (activity) => activity.status === "running",
  );
  const isAwaitingConfirmation = group.activities.some(
    (activity) => activity.status === "awaiting-confirmation",
  );
  const hasFailure = group.activities.some((activity) =>
    ["error", "denied", "rejected", "interrupted"].includes(activity.status),
  );
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!hasRunning) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [hasRunning]);

  const startedAt = group.activities.reduce<number | undefined>(
    (earliest, activity) =>
      activity.startedAt === undefined
        ? earliest
        : earliest === undefined
          ? activity.startedAt
          : Math.min(earliest, activity.startedAt),
    undefined,
  );
  const completedAt = hasRunning
    ? now
    : group.activities.reduce<number | undefined>(
        (latest, activity) =>
          activity.completedAt === undefined
            ? latest
            : latest === undefined
              ? activity.completedAt
              : Math.max(latest, activity.completedAt),
        undefined,
      );
  const duration = formatDuration(startedAt, completedAt ?? undefined);
  const countLabel =
    group.activities.length + (group.activities.length === 1 ? " step" : " steps");
  const statusLabel = isAwaitingConfirmation
    ? "Awaiting confirmation"
    : hasFailure
      ? "Needs attention"
      : hasRunning
        ? "Working"
        : duration
          ? "Worked for " + duration
          : "Completed";
  const StatusIcon = isAwaitingConfirmation
    ? ShieldCheck
    : hasFailure
      ? CircleAlert
      : hasRunning
        ? LoaderCircle
        : CheckCircle2;
  const GroupIcon = GROUP_ICONS[group.category];
  const contentId =
    "agent-activity-group-" + group.id.replace(/[^a-zA-Z0-9_-]/g, "-");

  return (
    <section
      className="agent-work-group"
      data-expanded={expanded ? "true" : "false"}
    >
      <button
        type="button"
        aria-expanded={expanded}
        aria-controls={contentId}
        aria-label={
          (expanded ? "Collapse " : "Expand ") +
          GROUP_LABELS[group.category] +
          ", " +
          countLabel
        }
        className={[
          "flex min-h-9 w-full min-w-0 cursor-pointer items-center gap-2 rounded-lg",
          "px-2.5 py-2 text-left",
          "transition-colors duration-200 hover:bg-[#f5f6f8]",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#0033ff]/20",
        ].join(" ")}
        onClick={onToggle}
      >
        <GroupIcon size={14} className="shrink-0 text-[#697789]" />
        <span className="min-w-0 truncate text-[13px] font-semibold text-[#344054]">
          {GROUP_LABELS[group.category]}
        </span>
        <span className="shrink-0 text-xs text-[#98a2b3]">{countLabel}</span>
        <span className="min-w-0 flex-1 truncate text-right text-xs text-[#98a2b3]">
          {statusLabel}
        </span>
        <StatusIcon
          size={13}
          className={
            "shrink-0 " +
            (isAwaitingConfirmation
              ? "text-[#b7791f]"
              : hasFailure
                ? "text-[#c45d5d]"
                : hasRunning
                  ? "animate-spin text-[#0033ff]"
                  : "text-[#7b9184]")
          }
        />
        <ChevronDown
          size={14}
          className={
            "shrink-0 text-[#8994a3] transition-transform duration-200 " +
            (expanded ? "rotate-180" : "")
          }
        />
      </button>

      <div
        id={contentId}
        className="agent-work-group__content"
        aria-hidden={!expanded}
      >
        <div className="agent-work-group__content-inner">
          {group.activities.map((activity: AgentActivity) => (
            <AgentActivityItem
              key={activity.id}
              activity={activity}
              isStreaming={isStreaming}
              selectedActivityId={selectedActivityId}
              onSelectActivity={onSelectActivity}
              disabled={!expanded}
            />
          ))}
        </div>
      </div>
    </section>
  );
}
