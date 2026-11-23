import { AlertCircle } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
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
  const [showScrollToBottom, setShowScrollToBottom] = useState(false);

  const updateScrollToBottom = useCallback(() => {
    const container = scrollRef.current;
    if (!container) {
      setShowScrollToBottom(false);
      return;
    }

    const remaining = container.scrollHeight - container.scrollTop - container.clientHeight;
    setShowScrollToBottom(remaining > 48);
  }, [scrollRef]);

  useEffect(() => {
    const container = scrollRef.current;
    if (!container) return;

    const update = () => updateScrollToBottom();
    container.addEventListener("scroll", update, { passive: true });

    // Message content can grow while the agent streams, without adding a new message.
    const resizeObserver = new ResizeObserver(update);
    resizeObserver.observe(container);
    if (container.firstElementChild) {
      resizeObserver.observe(container.firstElementChild);
    }
    update();

    return () => {
      container.removeEventListener("scroll", update);
      resizeObserver.disconnect();
    };
  }, [scrollRef, updateScrollToBottom]);

  useEffect(() => {
    updateScrollToBottom();
  }, [messages, updateScrollToBottom]);

  const scrollToBottom = () => {
    const container = scrollRef.current;
    if (!container) return;
    container.scrollTo({ top: container.scrollHeight, behavior: "smooth" });
  };

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="relative flex min-h-0 flex-1 flex-col">
        <MessageList
          messages={messages}
          pendingConfirmation={pendingConfirmation}
          onResolveConfirmation={onResolveConfirmation}
          scrollRef={scrollRef}
          selectedMessageId={selectedMessageId}
          selectedActivityId={selectedActivityId}
          onSelectActivity={onSelectActivity}
        />
        <button
          type="button"
          aria-label="滚动到底部"
          title="滚动到底部"
          aria-hidden={!showScrollToBottom}
          tabIndex={showScrollToBottom ? 0 : -1}
          onClick={scrollToBottom}
          className={`absolute bottom-3 left-1/2 z-10 flex h-9 w-9 -translate-x-1/2 cursor-pointer items-center justify-center rounded-full border border-[#e5e7eb] bg-white text-[#374151] shadow-sm transition-all duration-300 ease-out hover:border-[#d1d5db] hover:bg-[#f9fafb] hover:shadow-md focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#fe2c55] motion-reduce:transition-none ${
            showScrollToBottom
              ? "translate-y-0 opacity-100"
              : "pointer-events-none translate-y-2 opacity-0"
          }`}
        >
          <svg
            aria-hidden="true"
            className="text-default"
            focusable="false"
            height="20"
            viewBox="0 0 20 20"
            width="20"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M10 3.75v11.5m0 0 4.25-4.25M10 15.25 5.75 11"
              fill="none"
              stroke="currentColor"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="1.4"
            />
          </svg>
        </button>
      </div>

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
