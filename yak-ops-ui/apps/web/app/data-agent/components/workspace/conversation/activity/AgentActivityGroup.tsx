import { useEffect, useId, useState } from "react";
import {
  Activity,
  ChevronDown,
  Hammer,
  Play,
  Search,
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
  exploring: "Explored",
  changed: "Made changes",
  ran: "Ran",
  other: "Activity",
};

const GROUP_ICONS = {
  exploring: Search,
  changed: Hammer,
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
  const instanceId = useId();

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
  const duration =
    startedAt !== undefined && (hasRunning || completedAt !== undefined)
      ? formatDuration(startedAt, completedAt)
      : "";
  const countLabel =
    group.activities.length + (group.activities.length === 1 ? " step" : " steps");
  const statusLabel = isAwaitingConfirmation
    ? "Awaiting confirmation"
    : hasFailure
      ? "Needs attention"
      : "";
  const GroupIcon = GROUP_ICONS[group.category];
  const contentId = "agent-activity-group-" + instanceId.replace(/:/g, "");

  return (
    <section
      className="agent-work-group"
      data-expanded={expanded ? "true" : "false"}
      data-active={hasRunning ? "true" : "false"}
      data-category={group.category}
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
        className="agent-work-group__trigger group"
        onClick={onToggle}
      >
        <span
          className="agent-work-group__icon"
          data-category={group.category}
          data-active={hasRunning ? "true" : "false"}
          aria-hidden="true"
        >
          <GroupIcon size={14} strokeWidth={1.6} />
        </span>

        {hasRunning ? (
          <span className="agent-activity-shimmer agent-work-group__label">
            {GROUP_LABELS[group.category]}
          </span>
        ) : (
          <span className="agent-work-group__label">
            {GROUP_LABELS[group.category]}
          </span>
        )}

        <span className="agent-work-group__meta">{countLabel}</span>

        {statusLabel ? (
          <span className="agent-work-group__status">{statusLabel}</span>
        ) : duration && hasRunning ? (
          <span className="agent-work-group__status">{duration}</span>
        ) : null}

        <ChevronDown
          size={13}
          className={
            "agent-work-group__chevron " +
            (expanded ? "agent-work-group__chevron--expanded" : "")
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
