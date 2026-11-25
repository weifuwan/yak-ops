import type { AgentActivity } from "../../../model/types";
import { AgentActivityDetails } from "./AgentActivityDetails";

type AgentDetailsPanelProps = {
  activity: AgentActivity;
  expandAll: boolean;
  messageError?: string;
};

export function AgentDetailsPanel({
  activity,
  expandAll,
  messageError,
}: AgentDetailsPanelProps) {
  return (
    <div className="relative flex h-full min-h-0 min-w-0 flex-col bg-white">
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain scrollbar-subtle">
        <AgentActivityDetails
          activity={activity}
          expandAll={expandAll}
          messageError={messageError}
        />
      </div>
    </div>
  );
}
