import type { AgentMessage } from "../../../model/types";
import { AgentDetailsTimeline } from "./AgentDetailsTimeline";

type AgentDetailsPanelProps = {
  message: AgentMessage;
  selectedActivityId: string;
  expandAll: boolean;
};

export function AgentDetailsPanel({
  message,
  selectedActivityId,
  expandAll,
}: AgentDetailsPanelProps) {
  const activities = message.activities ?? [];

  return (
    <div className="relative flex h-full min-h-0 min-w-0 flex-col bg-white">
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain scrollbar-subtle px-3 pb-6 pt-3">
        {activities.length > 0 ? (
          <AgentDetailsTimeline
            activities={activities}
            selectedActivityId={selectedActivityId}
            expandAll={expandAll}
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
