import { CircleAlert, LoaderCircle } from "lucide-react";

import { DataAgentMarkdown } from "../../markdown/DataAgentMarkdown";
import type { AgentMessage, PendingConfirmation } from "../../../model/types";
import { AgentActivityTimeline } from "./activity/AgentActivityTimeline";
import { ConfirmationCard } from "./ConfirmationCard";
import { MessageError } from "./MessageError";
import { ToolCallList } from "./ToolCallList";

type AssistantMessageProps = {
  message: AgentMessage;
  pendingConfirmation: PendingConfirmation | null;
  onResolveConfirmation: (approved: boolean) => void;
  selectedActivityId?: string | null;
  onSelectActivity?: (activityId: string) => void;
};

export function AssistantMessage({
  message,
  pendingConfirmation,
  onResolveConfirmation,
  selectedActivityId,
  onSelectActivity,
}: AssistantMessageProps) {
  const toolCalls = message.toolCalls ?? [];
  const activities = message.activities ?? [];
  const hasTimeline = activities.some((activity) => activity.kind !== "text");
  const reachedMaxIterations = message.replyEnds?.some(
    (reply) => reply.finishedReason === "exceed_max_iters",
  );

  return (
    <div className="min-w-0 text-[14px] leading-7 text-[#27302f]">
      {hasTimeline ? (
        <AgentActivityTimeline
          activities={activities}
          isStreaming={message.status === "streaming"}
          selectedActivityId={selectedActivityId}
          onSelectActivity={onSelectActivity}
        />
      ) : message.content.trim() ? (
        <DataAgentMarkdown
          content={message.content}
          isStreaming={message.status === "streaming"}
        />
      ) : message.status === "streaming" && toolCalls.length === 0 ? (
        <div className="flex items-center gap-2 text-xs text-[#899391]">
          <LoaderCircle size={14} className="animate-spin" />
          正在准备执行...
        </div>
      ) : null}

      {!hasTimeline && toolCalls.length > 0 ? (
        <ToolCallList toolCalls={toolCalls} />
      ) : null}

      {pendingConfirmation?.assistantMessageId === message.id ? (
        <ConfirmationCard
          event={pendingConfirmation.event}
          onResolve={onResolveConfirmation}
        />
      ) : null}

      {reachedMaxIterations ? (
        <div className="mt-2 flex items-start gap-2 rounded-lg bg-[#fff8ec] px-3 py-2 text-xs leading-5 text-[#8a6a32]">
          <CircleAlert size={14} className="mt-0.5 shrink-0" />
          <span>已达到本次 Agent 最大执行轮次，你可以继续发送消息让它接着处理。</span>
        </div>
      ) : null}

      {message.error ? <MessageError message={message.error} /> : null}
    </div>
  );
}
