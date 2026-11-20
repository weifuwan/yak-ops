import { AlertCircle } from "lucide-react";
import type { ReactNode, RefObject } from "react";

import { DataAgentPromptComposer } from "../DataAgentPromptComposer";
import type { AgentMessage, PendingConfirmation } from "../../model/types";
import { MessageList } from "./conversation/MessageList";

type ConversationPanelProps = {
  messages: AgentMessage[];
  pendingConfirmation: PendingConfirmation | null;
  onResolveConfirmation: (approved: boolean) => void;
  scrollRef: RefObject<HTMLDivElement>;
  selectedMessageId?: string | null;
  selectedActivityId?: string | null;
  onSelectActivity?: (messageId: string, activityId: string) => void;
  connectionError: string;
  composerKey: number;
  initialDraft: string;
  isSending: boolean;
  onSubmit: (content: string) => boolean | void;
  trailingActions: ReactNode;
};

export function ConversationPanel({
  messages,
  pendingConfirmation,
  onResolveConfirmation,
  scrollRef,
  selectedMessageId,
  selectedActivityId,
  onSelectActivity,
  connectionError,
  composerKey,
  initialDraft,
  isSending,
  onSubmit,
  trailingActions,
}: ConversationPanelProps) {
  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <MessageList
        messages={messages}
        pendingConfirmation={pendingConfirmation}
        onResolveConfirmation={onResolveConfirmation}
        scrollRef={scrollRef}
        selectedMessageId={selectedMessageId}
        selectedActivityId={selectedActivityId}
        onSelectActivity={onSelectActivity}
      />

      <div className="shrink-0 px-6 pb-5 pt-3 max-sm:px-4">
        <div className="mx-auto w-full max-w-3xl">
          {connectionError ? (
            <div
              role="alert"
              className="mb-2 flex items-start gap-2 rounded-xl border border-[#f0d6d6] bg-[#fff7f7] px-3 py-2.5 text-xs leading-5 text-[#a14d4d]"
            >
              <AlertCircle size={14} className="mt-0.5 shrink-0" />
              <span>{connectionError}</span>
            </div>
          ) : null}
          <DataAgentPromptComposer
            key={composerKey}
            initialValue={initialDraft}
            onSubmit={onSubmit}
            isSubmitting={isSending}
            trailingActions={trailingActions}
            placeholder="继续向 Data Agent 提问..."
          />
          <p className="mb-0 mt-2 text-center text-[11px] text-[#aaaaaa]">
            Agent 正在实时处理消息；涉及写操作时，需要你确认后才会执行
          </p>
        </div>
      </div>
    </div>
  );
}
