import { useEffect, useRef, useState } from "react";
import { AlertCircle, X } from "lucide-react";
import type { ReactNode, RefObject } from "react";

import { DataAgentPromptComposer } from "../DataAgentPromptComposer";
import type { AgentActivity, AgentMessage, PendingConfirmation } from "../../model/types";
import { AgentDetailsResizer } from "./AgentDetailsResizer";
import { AgentDetailsPanel } from "./conversation/AgentDetailsPanel";
import { MessageList } from "./conversation/MessageList";

const DETAILS_MIN_WIDTH = 280;
const DETAILS_MAX_WIDTH = 560;

type ConversationPanelProps = {
  messages: AgentMessage[];
  pendingConfirmation: PendingConfirmation | null;
  onResolveConfirmation: (approved: boolean) => void;
  scrollRef: RefObject<HTMLDivElement>;
  connectionError: string;
  composerKey: number;
  initialDraft: string;
  isSending: boolean;
  onSubmit: (content: string) => boolean | void;
  trailingActions: ReactNode;
};

type ActivitySelection = {
  messageId: string;
  activityId: string;
};

export function ConversationPanel({
  messages,
  pendingConfirmation,
  onResolveConfirmation,
  scrollRef,
  connectionError,
  composerKey,
  initialDraft,
  isSending,
  onSubmit,
  trailingActions,
}: ConversationPanelProps) {
  const [selection, setSelection] = useState<ActivitySelection | null>(null);
  const [detailsWidth, setDetailsWidth] = useState(420);
  const [detailsMaxWidth, setDetailsMaxWidth] = useState(DETAILS_MAX_WIDTH);
  const [detailsResizing, setDetailsResizing] = useState(false);
  const conversationLayoutRef = useRef<HTMLDivElement>(null);

  const selectedMessage =
    selection === null
      ? null
      : messages.find((message) => message.id === selection.messageId) ?? null;
  const selectedActivity: AgentActivity | null =
    selectedMessage?.activities?.find(
      (activity) => activity.id === selection?.activityId,
    ) ?? null;
  const detailsOpen = Boolean(selectedMessage && selectedActivity);

  useEffect(() => {
    const element = conversationLayoutRef.current;
    if (!element) return;

    const updateMaxWidth = () => {
      const nextMaxWidth = Math.min(
        DETAILS_MAX_WIDTH,
        Math.max(DETAILS_MIN_WIDTH, Math.floor(element.clientWidth * 0.55)),
      );
      setDetailsMaxWidth(nextMaxWidth);
      setDetailsWidth((current) =>
        Math.min(nextMaxWidth, Math.max(DETAILS_MIN_WIDTH, current)),
      );
    };

    updateMaxWidth();
    if (typeof ResizeObserver === "undefined") {
      window.addEventListener("resize", updateMaxWidth);
      return () => window.removeEventListener("resize", updateMaxWidth);
    }

    const observer = new ResizeObserver(updateMaxWidth);
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (selection && (!selectedMessage || !selectedActivity)) {
      setSelection(null);
    }
  }, [selection, selectedMessage, selectedActivity]);

  useEffect(() => {
    if (!detailsOpen) return;
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setSelection(null);
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [detailsOpen]);

  function handleSelectActivity(messageId: string, activityId: string) {
    setSelection({ messageId, activityId });
  }

  function closeDetails() {
    setSelection(null);
  }

  return (
    <div
      ref={conversationLayoutRef}
      className="relative flex min-h-0 min-w-0 flex-1"
    >
      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <MessageList
          messages={messages}
          pendingConfirmation={pendingConfirmation}
          onResolveConfirmation={onResolveConfirmation}
          scrollRef={scrollRef}
          selectedMessageId={selection?.messageId}
          selectedActivityId={selection?.activityId}
          onSelectActivity={handleSelectActivity}
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

      {detailsOpen ? (
        <AgentDetailsResizer
          width={detailsWidth}
          maxWidth={detailsMaxWidth}
          onResize={setDetailsWidth}
          onDraggingChange={setDetailsResizing}
        />
      ) : null}

      <aside
        id="agent-details-panel"
        aria-label="Agent 执行详情"
        aria-hidden={!detailsOpen}
        style={{ width: detailsOpen ? String(detailsWidth) + "px" : "0px" }}
        className={
          "relative flex h-full min-h-0 shrink-0 flex-col overflow-hidden bg-white motion-reduce:transition-none " +
          (detailsResizing
            ? "transition-none "
            : "transition-[width,opacity] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] ") +
          (detailsOpen
            ? "border-l border-[#ecefed] opacity-100 max-md:absolute max-md:inset-0 max-md:!w-full max-md:z-20"
            : "pointer-events-none border-l-0 opacity-0")
        }
      >
        {selectedMessage && selectedActivity ? (
          <AgentDetailsPanel
            message={selectedMessage}
            selectedActivityId={selectedActivity.id}
            onClose={closeDetails}
          />
        ) : null}
      </aside>
    </div>
  );
}
