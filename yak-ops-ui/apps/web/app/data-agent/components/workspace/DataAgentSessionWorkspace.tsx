import { useEffect, useRef, useState, type ReactNode, type RefObject } from "react";
import { Activity, PanelRightClose } from "lucide-react";

import type { AgentActivity, AgentMessage, PendingConfirmation } from "../../model/types";
import type { AgentSessionUsage } from "@/service/data-agent";
import { TOOL_LABELS } from "../../model/constants";
import { AgentDetailsResizer } from "./conversation/AgentDetailsResizer";
import { AgentDetailsPanel } from "./conversation/AgentDetailsPanel";
import { ConversationPanel } from "./ConversationPanel";
import { DataAgentConversationHeader } from "./DataAgentConversationHeader";

const DETAILS_MIN_WIDTH = 320;
const DETAILS_MAX_WIDTH = 640;

function getActivityDisplayName(activity: AgentActivity | null): string {
  if (!activity) return "Details";
  if (activity.kind !== "tool") return activity.title;

  return (
    TOOL_LABELS[activity.toolName ?? ""] ??
    activity.toolName ??
    activity.title
  );
}

type ActivitySelection = {
  messageId: string;
  activityId: string;
};

type DataAgentSessionWorkspaceProps = {
  sessionId: string;
  title: string;
  isSending: boolean;
  isReconnecting: boolean;
  sessionUsage: AgentSessionUsage;
  sessionNotice: string;
  onStop: () => void;
  sidebarOpen: boolean;
  onToggleSidebar: () => void;
  messages: AgentMessage[];
  pendingConfirmation: PendingConfirmation | null;
  onResolveConfirmation: (approved: boolean) => void;
  scrollRef: RefObject<HTMLDivElement>;
  connectionError: string;
  composerKey: number;
  initialDraft: string;
  onSubmit: (content: string) => boolean | void;
  trailingActions: ReactNode;
};

function MobileDetailsHeader({
  detailsTitle,
  onClose,
}: {
  detailsTitle: string;
  onClose: () => void;
}) {
  return (
    <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#ecefed] bg-white px-3 lg:hidden">
      <span
        title={detailsTitle}
        className="max-w-[45%] truncate text-xs font-semibold text-[#303936]"
      >
        {detailsTitle}
      </span>
      <div className="flex items-center gap-1">
        <span className="flex h-7 items-center rounded-md border border-[#dfe4e2] bg-[#f3f5f4] px-2 text-[11px] font-medium text-[#3f4945]">
          Timeline
        </span>
        <button
          type="button"
          disabled
          title="变更记录暂未接入"
          className="flex h-7 cursor-not-allowed items-center rounded-md px-2 text-[11px] text-[#a6aeaa]"
        >
          Changes
        </button>
        <button
          type="button"
          aria-label="收起执行详情"
          title="收起执行详情"
          className="flex size-7 cursor-pointer items-center justify-center rounded-md text-[#87928f] transition-colors hover:bg-[#f2f4f3] hover:text-[#303936]"
          onClick={onClose}
        >
          <PanelRightClose size={15} strokeWidth={1.5} />
        </button>
      </div>
    </div>
  );
}

function DetailsEmptyState() {
  return (
    <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-2 px-8 text-center">
      <Activity size={20} strokeWidth={1.5} className="text-[#a1aaa6]" />
      <span className="text-xs font-medium text-[#59635e]">执行详情</span>
      <p className="m-0 max-w-[240px] text-[11px] leading-5 text-[#929b97]">
        选择左侧的工具调用，查看对应的输入参数和执行结果
      </p>
    </div>
  );
}

export function DataAgentSessionWorkspace({
  sessionId,
  title,
  isSending,
  isReconnecting,
  sessionUsage,
  sessionNotice,
  onStop,
  sidebarOpen,
  onToggleSidebar,
  messages,
  pendingConfirmation,
  onResolveConfirmation,
  scrollRef,
  connectionError,
  composerKey,
  initialDraft,
  onSubmit,
  trailingActions,
}: DataAgentSessionWorkspaceProps) {
  const [selection, setSelection] = useState<ActivitySelection | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [detailsExpandAll, setDetailsExpandAll] = useState(false);
  const [detailsWidth, setDetailsWidth] = useState(420);
  const [detailsMaxWidth, setDetailsMaxWidth] = useState(DETAILS_MAX_WIDTH);
  const [detailsResizing, setDetailsResizing] = useState(false);
  const layoutRef = useRef<HTMLDivElement>(null);
  const widthInitializedRef = useRef(false);

  const selectedMessage =
    selection === null
      ? null
      : messages.find((message) => message.id === selection.messageId) ?? null;
  const selectedActivity: AgentActivity | null =
    selectedMessage?.activities?.find(
      (activity) => activity.id === selection?.activityId,
    ) ?? null;
  const selectedActivityTitle = getActivityDisplayName(selectedActivity);

  useEffect(() => {
    const element = layoutRef.current;
    if (!element) return;

    const updateWidth = () => {
      const availableWidth = element.clientWidth;
      if (availableWidth <= 0) return;

      const nextMaxWidth = Math.min(
        DETAILS_MAX_WIDTH,
        Math.max(DETAILS_MIN_WIDTH, Math.floor(availableWidth * 0.55)),
      );
      const firstMeasurement = !widthInitializedRef.current;
      widthInitializedRef.current = true;

      setDetailsMaxWidth(nextMaxWidth);
      setDetailsWidth((current) =>
        firstMeasurement
          ? Math.min(nextMaxWidth, Math.max(DETAILS_MIN_WIDTH, Math.round(availableWidth * 0.4)))
          : Math.min(nextMaxWidth, Math.max(DETAILS_MIN_WIDTH, current)),
      );
    };

    updateWidth();
    if (typeof ResizeObserver === "undefined") {
      window.addEventListener("resize", updateWidth);
      return () => window.removeEventListener("resize", updateWidth);
    }

    const observer = new ResizeObserver(updateWidth);
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    setSelection(null);
    setDetailsOpen(false);
    setDetailsExpandAll(false);
  }, [sessionId]);

  useEffect(() => {
    if (selection && (!selectedMessage || !selectedActivity)) {
      setSelection(null);
    }
  }, [selection, selectedMessage, selectedActivity]);

  useEffect(() => {
    if (!detailsOpen) return;

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setDetailsOpen(false);
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [detailsOpen]);

  function handleSelectActivity(messageId: string, activityId: string) {
    setSelection({ messageId, activityId });
    setDetailsExpandAll(true);
    setDetailsOpen(true);
  }

  function handleBackToLatest() {
    const container = scrollRef.current;
    if (!container) return;
    container.scrollTo({ top: container.scrollHeight, behavior: "smooth" });
  }

  function toggleDetails() {
    setDetailsOpen((open) => !open);
  }

  return (
    <>
      <DataAgentConversationHeader
        title={title}
        isSending={isSending}
        isReconnecting={isReconnecting}
        sessionUsage={sessionUsage}
        sidebarOpen={sidebarOpen}
        detailsOpen={detailsOpen}
        metricsMessage={selectedMessage}
        detailsWidth={detailsWidth}
        detailsTitle={selectedActivityTitle}
        expandAll={detailsExpandAll}
        onToggleSidebar={onToggleSidebar}
        onToggleDetails={toggleDetails}
        onToggleExpandAll={() => setDetailsExpandAll((expanded) => !expanded)}
        onBackToLatest={handleBackToLatest}
      />

      <section
        ref={layoutRef}
        className="relative flex min-h-0 min-w-0 flex-1 overflow-hidden bg-white"
      >
        <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
          <ConversationPanel
            messages={messages}
            pendingConfirmation={pendingConfirmation}
            onResolveConfirmation={onResolveConfirmation}
            scrollRef={scrollRef}
            selectedMessageId={selection?.messageId}
            selectedActivityId={selection?.activityId}
            onSelectActivity={handleSelectActivity}
            connectionError={connectionError}
            sessionNotice={sessionNotice}
            composerKey={composerKey}
            initialDraft={initialDraft}
            isSending={isSending}
            onSubmit={onSubmit}
            onStop={onStop}
            trailingActions={trailingActions}
          />
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
          ref={(element) => {
            if (element) element.inert = !detailsOpen;
          }}
          aria-label="Agent 执行详情"
          aria-hidden={!detailsOpen}
          style={{ width: detailsOpen ? String(detailsWidth) + "px" : "0px" }}
          className={
            "relative flex h-full min-h-0 shrink-0 flex-col overflow-hidden bg-transparent motion-reduce:transition-none " +
            (detailsResizing
              ? "transition-none "
              : "transition-[width,opacity] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] ") +
            (detailsOpen
              ? "p-2 opacity-100 max-lg:absolute max-lg:inset-0 max-lg:z-20 max-lg:!w-full"
              : "pointer-events-none p-0 opacity-0")
          }
        >
          <div className="flex h-full min-h-0 min-w-0 flex-col overflow-hidden rounded-[14px] border border-[#e5e5e5] bg-white shadow-surface-xl">
            <MobileDetailsHeader
              detailsTitle={selectedActivityTitle}
              onClose={() => setDetailsOpen(false)}
            />
            {selectedMessage && selectedActivity ? (
              <AgentDetailsPanel
                activity={selectedActivity}
                expandAll={detailsExpandAll}
                messageError={selectedMessage.error}
              />
            ) : (
              <DetailsEmptyState />
            )}
          </div>
        </aside>
      </section>
    </>
  );
}

export default DataAgentSessionWorkspace;
