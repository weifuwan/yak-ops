import { X } from "lucide-react";

import type { AgentMessage } from "../../../model/types";
import { AgentDetailsTimeline } from "./AgentDetailsTimeline";

type AgentDetailsPanelProps = {
  message: AgentMessage;
  selectedActivityId: string;
  onClose: () => void;
};

export function AgentDetailsPanel({
  message,
  selectedActivityId,
  onClose,
}: AgentDetailsPanelProps) {
  const activities = message.activities ?? [];

  return (
    <div className="relative flex h-full min-h-0 min-w-0 flex-col bg-white">
      <button
        type="button"
        aria-label="关闭执行详情"
        title="关闭"
        className="absolute right-3 top-2 z-20 flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-md bg-white/90 text-[#87928f] transition-colors hover:bg-[#f2f4f3] hover:text-[#303936] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25"
        onClick={onClose}
      >
        <X size={15} />
      </button>

      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain scrollbar-subtle px-4 pb-6 pt-3">
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
            className="ml-8 mt-3 rounded-lg border border-[#f0d6d6] bg-[#fff7f7] px-3 py-2 text-xs leading-5 text-[#a14d4d]"
          >
            {message.error}
          </div>
        ) : null}
      </div>
    </div>
  );
}
