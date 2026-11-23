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
  sessionNotice: string;
  composerKey: number;
  initialDraft: string;
  isSending: boolean;
  onSubmit: (content: string) => boolean | void;
  onStop: () => void;
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
  sessionNotice,
  composerKey,
  initialDraft,
  isSending,
  onSubmit,
  onStop,
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
          {sessionNotice ? (
            <div className="mb-2 rounded-xl border border-[#f0e3c7] bg-[#fffaf0] px-3 py-2 text-xs leading-5 text-[#8a6a32]">
              {sessionNotice}
            </div>
          ) : null}
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
            onStop={onStop}
            trailingActions={trailingActions}
            placeholder="继续向 Data Agent 提问..."
          />
          <p className="mb-0 mt-2 text-center text-[11px] text-[#aaaaaa]">
            Agent 运行时仍可继续发送，新消息会按顺序排队；涉及写操作时需要你确认
          </p>
        </div>
      </div>
    </div>
  );
}
