import { DataAgentMarkdown } from "../../../markdown/DataAgentMarkdown";
import type { AgentActivity } from "../../../../model/types";
import { AgentThinkingIndicator } from "./AgentThinkingIndicator";
import { AgentToolActivity } from "./AgentToolActivity";

type AgentActivityItemProps = {
  activity: AgentActivity;
  disabled?: boolean;
  isStreaming: boolean;
  selectedActivityId?: string | null;
  onSelectActivity?: (activityId: string) => void;
};

export function AgentActivityItem({
  activity,
  disabled = false,
  isStreaming,
  selectedActivityId,
  onSelectActivity,
}: AgentActivityItemProps) {
  if (activity.kind === "text") {
    return (
      <div className="min-w-0 py-1 text-[14px] leading-7 text-[#27302f]">
        <DataAgentMarkdown
          content={activity.content ?? ""}
          isStreaming={isStreaming && activity.status === "running"}
        />
      </div>
    );
  }

  if (activity.kind === "tool") {
    return (
      <AgentToolActivity
        activity={activity}
        disabled={disabled}
        selected={selectedActivityId === activity.id}
        onSelect={onSelectActivity}
      />
    );
  }

  return <AgentThinkingIndicator activity={activity} />;
}
