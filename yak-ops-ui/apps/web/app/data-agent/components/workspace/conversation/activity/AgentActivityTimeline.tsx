import type { AgentActivity } from "../../../../model/types";
import { AgentActivityItem } from "./AgentActivityItem";

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
  return (
    <div className="my-2 flex min-w-0 flex-col gap-1">
      {activities.map((activity) => (
        <AgentActivityItem
          key={activity.id}
          activity={activity}
          isStreaming={isStreaming}
          selectedActivityId={selectedActivityId}
          onSelectActivity={onSelectActivity}
        />
      ))}
    </div>
  );
}
