import { useState } from "react";

import type { AgentActivity } from "../../../../model/types";
import { AgentActivityItem } from "./AgentActivityItem";
import { AgentActivityGroup as AgentActivityGroupView } from "./AgentActivityGroup";
import { buildActivityTimeline } from "./activity-presenter";

type AgentActivityTimelineProps = {
  activities: AgentActivity[];
  isStreaming: boolean;
  selectedActivityId?: string | null;
  onSelectActivity?: (activityId: string) => void;
};

export function AgentActivityTimeline({
  activities,
  isStreaming,
  selectedActivityId,
  onSelectActivity,
}: AgentActivityTimelineProps) {
  const [expandedOverrides, setExpandedOverrides] = useState<
    Record<string, boolean>
  >({});
  const entries = buildActivityTimeline(activities);

  return (
    <div className="my-2 flex min-w-0 flex-col gap-1">
      {entries.map((entry) => {
        if (entry.type === "activity") {
          return (
            <AgentActivityItem
              key={entry.id}
              activity={entry.activity}
              isStreaming={isStreaming}
              selectedActivityId={selectedActivityId}
              onSelectActivity={onSelectActivity}
            />
          );
        }

        const group = entry.group;
        const containsSelectedActivity = group.activities.some(
          (activity) => activity.id === selectedActivityId,
        );
        const defaultExpanded =
          isStreaming || group.hasRunning || group.requiresAttention;
        const expanded = containsSelectedActivity
          ? true
          : expandedOverrides[group.id] ?? defaultExpanded;

        return (
          <AgentActivityGroupView
            key={entry.id}
            group={group}
            expanded={expanded}
            isStreaming={isStreaming}
            selectedActivityId={selectedActivityId}
            onSelectActivity={onSelectActivity}
            onToggle={() =>
              setExpandedOverrides((current) => ({
                ...current,
                [group.id]: !expanded,
              }))
            }
          />
        );
      })}
    </div>
  );
}
