import { useEffect, useRef, useState, type CSSProperties } from "react";

import { useDataAgentConversation } from "@/hooks/useDataAgentConversation";

import { DataAgentSidebar } from "./DataAgentSidebar";
import { DataAgentSidebarResizer } from "./DataAgentSidebarResizer";
import { DataAgentConversationHeader } from "./workspace/DataAgentConversationHeader";
import { ConversationPanel } from "./workspace/ConversationPanel";
import { DataAgentEmptyState } from "./workspace/DataAgentEmptyState";
import { DataAgentLoading } from "./workspace/DataAgentLoading";
import { ModelSelector } from "./workspace/ModelSelector";

type DataAgentWorkspaceProps = {
  initialPrompt?: string;
};

export function DataAgentWorkspace({
  initialPrompt = "",
}: DataAgentWorkspaceProps) {
  const {
    conversations,
    activeConversationId,
    activeConversation,
    messages,
    lastMessage,
    isSending,
    isLoading,
    connectionError,
    pendingConfirmation,
    createConversation,
    selectConversation,
    sendMessage,
    resolveConfirmation,
  } = useDataAgentConversation();

  const [selectedModel, setSelectedModel] = useState("DeepSeek");
  const [modelMenuOpen, setModelMenuOpen] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [sidebarWidth, setSidebarWidth] = useState(256);
  const [sidebarResizing, setSidebarResizing] = useState(false);
  const [composerKey, setComposerKey] = useState(0);
  const [initialDraft, setInitialDraft] = useState(initialPrompt);
  const messageScrollRef = useRef<HTMLDivElement>(null);

  const hasMessages = messages.length > 0;
  const [showLoadingOverlay, setShowLoadingOverlay] = useState(isLoading);
  const isLoadingOverlayVisible = isLoading || showLoadingOverlay;
  const [loadingOverlayExiting, setLoadingOverlayExiting] = useState(false);

  useEffect(() => {
    if (isLoading) {
      setShowLoadingOverlay(true);
      setLoadingOverlayExiting(false);
      return;
    }
    if (!showLoadingOverlay) return;

    setLoadingOverlayExiting(true);
    const timeout = window.setTimeout(() => setShowLoadingOverlay(false), 200);
    return () => window.clearTimeout(timeout);
  }, [isLoading, showLoadingOverlay]);

  useEffect(() => {
    const container = messageScrollRef.current;
    if (!container || !hasMessages) return;
    container.scrollTop = container.scrollHeight;
  }, [
    activeConversationId,
    hasMessages,
    messages.length,
    lastMessage?.content,
    lastMessage?.status,
    lastMessage?.toolCalls?.length,
    pendingConfirmation?.event.replyId,
  ]);

  function handleNewConversation() {
    if (isSending) return;
    createConversation();
    setModelMenuOpen(false);
    setInitialDraft("");
    setComposerKey((current) => current + 1);
  }

  function handleSelectConversation(id: string) {
    if (isSending) return;
    selectConversation(id);
    setModelMenuOpen(false);
    setInitialDraft("");
    setComposerKey((current) => current + 1);
  }

  function handlePromptSubmit(content: string) {
    if (isSending) return false;
    setInitialDraft("");
    void sendMessage(content).then((result) => {
      if (!result.restoreDraft) return;
      setInitialDraft(content);
      setComposerKey((current) => current + 1);
    });
    return true;
  }

  function handleQuickAction(prompt: string) {
    if (isSending) return;
    setInitialDraft(prompt);
    setComposerKey((current) => current + 1);
  }

  const modelSelector = (
    <ModelSelector
      value={selectedModel}
      open={modelMenuOpen}
      onOpenChange={setModelMenuOpen}
      onSelect={(value) => {
        setSelectedModel(value);
        setModelMenuOpen(false);
      }}
    />
  );

  return (
    <>
      {isLoadingOverlayVisible ? (
        <DataAgentLoading sidebarWidth={sidebarWidth} exiting={loadingOverlayExiting && !isLoading} />
      ) : null}
      <div
        aria-hidden={isLoadingOverlayVisible}
        ref={(element) => {
          if (element) element.inert = isLoadingOverlayVisible;
        }}
        className={
          (isLoading ? "" : "data-agent-workspace-enter ") +
          "flex min-h-0 min-w-0 flex-1 overflow-hidden bg-[#f0f0f0] text-[#20201e]"
        }
      >
        <div
          aria-hidden={!sidebarOpen}
          className={
            "h-full shrink-0 overflow-hidden bg-[#f0f0f0] " +
            (sidebarResizing
              ? "transition-none "
              : "transition-[width] duration-[320ms] ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none ") +
            "w-[var(--data-agent-sidebar-width)] max-md:!w-0"
          }
          style={
            {
              "--data-agent-sidebar-width": sidebarOpen ? sidebarWidth + "px" : "0px",
            } as CSSProperties
          }
        >
          <div
            ref={(element) => {
              if (element) element.inert = !sidebarOpen;
            }}
            className={
              "h-full w-full transform-gpu transition-[transform,opacity] duration-[320ms] ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none " +
              (sidebarOpen
                ? "translate-x-0 opacity-100 pointer-events-auto"
                : "-translate-x-3 opacity-0 pointer-events-none")
            }
          >
            <DataAgentSidebar
              conversations={conversations}
              activeConversationId={activeConversationId}
              busy={isSending}
              onNewConversation={handleNewConversation}
              onSelectConversation={handleSelectConversation}
            />
          </div>
        </div>

        {sidebarOpen ? (
          <DataAgentSidebarResizer
            width={sidebarWidth}
            onResize={setSidebarWidth}
            onDraggingChange={setSidebarResizing}
          />
        ) : null}

        <main className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-white">
          <DataAgentConversationHeader
            title={activeConversation?.title ?? "新对话"}
            isSending={isSending}
            sidebarOpen={sidebarOpen}
            onToggleSidebar={() => setSidebarOpen((open) => !open)}
          />

          <section className="relative flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-white">
            {!hasMessages ? (
              <DataAgentEmptyState
                connectionError={connectionError}
                restored={Boolean(activeConversation?.restored)}
                composerKey={composerKey}
                initialDraft={initialDraft}
                isSending={isSending}
                onSubmit={handlePromptSubmit}
                onQuickAction={handleQuickAction}
                trailingActions={modelSelector}
              />
            ) : (
              <ConversationPanel
                messages={messages}
                pendingConfirmation={pendingConfirmation}
                onResolveConfirmation={resolveConfirmation}
                scrollRef={messageScrollRef}
                connectionError={connectionError}
                composerKey={composerKey}
                initialDraft={initialDraft}
                isSending={isSending}
                onSubmit={handlePromptSubmit}
                trailingActions={modelSelector}
              />
            )}
          </section>
        </main>
      </div>
    </>
  );
}

export default DataAgentWorkspace;
