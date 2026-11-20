import type { RefObject } from "react";

import type { AgentMessage, PendingConfirmation } from "../../../model/types";
import { MessageItem } from "./MessageItem";

type MessageListProps = {
  messages: AgentMessage[];
  pendingConfirmation: PendingConfirmation | null;
  onResolveConfirmation: (approved: boolean) => void;
  scrollRef: RefObject<HTMLDivElement>;
  selectedMessageId?: string | null;
  selectedActivityId?: string | null;
  onSelectActivity?: (messageId: string, activityId: string) => void;
};

export function MessageList({
  messages,
  pendingConfirmation,
  onResolveConfirmation,
  scrollRef,
  selectedMessageId,
  selectedActivityId,
  onSelectActivity,
}: MessageListProps) {
  return (
    <div
      ref={scrollRef}
      className="min-h-0 flex-1 overflow-y-auto overscroll-contain scrollbar-subtle"
    >
      <div
        className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-6 pb-8 pt-14 max-sm:px-4"
        style={{ paddingLeft: 0, paddingRight: 0 }}
      >
        {messages.map((message) => (
          <MessageItem
            key={message.id}
            message={message}
            pendingConfirmation={pendingConfirmation}
            onResolveConfirmation={onResolveConfirmation}
            selectedActivityId={
              selectedMessageId === message.id ? selectedActivityId : null
            }
            onSelectActivity={
              onSelectActivity
                ? (activityId) => onSelectActivity(message.id, activityId)
                : undefined
            }
          />
        ))}
      </div>
    </div>
  );
}
