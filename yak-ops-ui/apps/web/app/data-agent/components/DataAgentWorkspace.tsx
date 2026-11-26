import { useEffect, useRef, useState, type CSSProperties } from "react";
import { PanelLeft } from "lucide-react";

import { useDataAgentConversation } from "@/hooks/useDataAgentConversation";
import type { AgentSessionContext } from "@/service/data-agent";

import { DataAgentSidebar } from "./DataAgentSidebar";
import { DataAgentSidebarResizer } from "./DataAgentSidebarResizer";
import { DataAgentSessionWorkspace } from "./workspace/DataAgentSessionWorkspace";
import { DataAgentSessionLoading } from "./workspace/DataAgentSessionLoading";
import { DataAgentEmptyState } from "./workspace/DataAgentEmptyState";
import { DataAgentLoading } from "./workspace/DataAgentLoading";
import { ModelSelector } from "./workspace/ModelSelector";
import { DataAgentContextUsage } from "./workspace/DataAgentContextUsage";
import MagicPatternsParticleTransition, {
  type MagicPatternsParticleTransitionHandle,
} from "./MagicPatternsParticleTransition";

type DataAgentWorkspaceProps = {
  sessionId: string | null;
  initialPrompt?: string;
  enableInitialParticleTransition?: boolean;
  onSessionCreated: (sessionId: string) => void;
  onNewConversation: () => void;
  onSelectConversation: (sessionId: string) => void;
};

export function DataAgentWorkspace({
  sessionId,
  initialPrompt = "",
  enableInitialParticleTransition = false,
  onSessionCreated,
  onNewConversation,
  onSelectConversation,
}: DataAgentWorkspaceProps) {
  const {
    conversations,
    activeConversationId,
    activeConversation,
    messages,
    lastMessage,
    isSending,
    isReconnecting,
    isLoading,
    isRestoringSession,
    connectionError,
    sessionNotice,
    sessionUsage,
    loadSessionContext,
    pendingConfirmation,
    createConversation,
    selectConversation,
    sendMessage,
    resolveConfirmation,
    stopCurrentTurn,
    renameConversation,
    deleteConversation,
  } = useDataAgentConversation({ sessionId, onSessionCreated });

  const [selectedModel, setSelectedModel] = useState("DeepSeek");
  const [modelMenuOpen, setModelMenuOpen] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [sidebarWidth, setSidebarWidth] = useState(256);
  const [sidebarResizing, setSidebarResizing] = useState(false);
  const [composerKey, setComposerKey] = useState(0);
  const [initialDraft, setInitialDraft] = useState(initialPrompt);
  const [sessionContextSnapshot, setSessionContextSnapshot] = useState<{
    sessionId: string;
    context: AgentSessionContext | null;
  }>({ sessionId: "", context: null });
  const [sessionContextLoading, setSessionContextLoading] = useState(false);
  const [sessionContextError, setSessionContextError] = useState("");
  const activeSessionContextTokens =
    conversations.find((conversation) => conversation.id === activeConversationId)?.contextTokens ?? 0;
  const sessionContext =
    sessionContextSnapshot.sessionId === activeConversationId
      ? sessionContextSnapshot.context
      : null;
  const messageScrollRef = useRef<HTMLDivElement>(null);
  const particleTransitionRef = useRef<MagicPatternsParticleTransitionHandle | null>(null);
  const particleSourceRef = useRef<HTMLDivElement>(null);
  const particleTargetRef = useRef<HTMLDivElement>(null);
  const [showInitialParticleTransition, setShowInitialParticleTransition] =
    useState(enableInitialParticleTransition);
  const initialParticleTransitionPendingRef = useRef(enableInitialParticleTransition);

  const hasMessages = messages.length > 0;
  const [initialLoadComplete, setInitialLoadComplete] = useState(false);
  const shouldShowInitialLoading = isLoading && !initialLoadComplete;
  const [showLoadingOverlay, setShowLoadingOverlay] = useState(shouldShowInitialLoading);
  const isLoadingOverlayVisible = shouldShowInitialLoading || showLoadingOverlay;
  const [loadingOverlayExiting, setLoadingOverlayExiting] = useState(false);

  useEffect(() => {
    if (!activeConversationId) {
      setSessionContextLoading(false);
      setSessionContextError("");
      return;
    }

    const controller = new AbortController();
    setSessionContextLoading(true);
    setSessionContextError("");

    void loadSessionContext(activeConversationId, controller.signal)
      .then((context) => {
        if (!controller.signal.aborted) {
          setSessionContextSnapshot({ sessionId: activeConversationId, context });
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setSessionContextError(
            error instanceof Error ? error.message : "获取上下文用量失败",
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setSessionContextLoading(false);
      });

    return () => controller.abort();
  }, [activeConversationId, activeSessionContextTokens, loadSessionContext]);

  useEffect(() => {
    if (!isLoading) setInitialLoadComplete(true);
  }, [isLoading]);

  useEffect(() => {
    if (shouldShowInitialLoading) {
      setShowLoadingOverlay(true);
      setLoadingOverlayExiting(false);
      return;
    }
    if (!showLoadingOverlay) return;

    setLoadingOverlayExiting(true);
    const timeout = window.setTimeout(() => setShowLoadingOverlay(false), 200);
    return () => window.clearTimeout(timeout);
  }, [shouldShowInitialLoading, showLoadingOverlay]);

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
    particleTransitionRef.current?.cancel();
    initialParticleTransitionPendingRef.current = true;
    setShowInitialParticleTransition(true);
    createConversation();
    onNewConversation();
    setModelMenuOpen(false);
    setInitialDraft("");
    setComposerKey((current) => current + 1);
  }

  function handleSelectConversation(id: string) {
    if (isSending) return;
    particleTransitionRef.current?.cancel();
    initialParticleTransitionPendingRef.current = false;
    setShowInitialParticleTransition(false);
    selectConversation(id);
    onSelectConversation(id);
    setModelMenuOpen(false);
    setInitialDraft("");
    setComposerKey((current) => current + 1);
  }

  function handlePromptSubmit(content: string) {
    const shouldPlayInitialParticleTransition = initialParticleTransitionPendingRef.current;
    setInitialDraft("");
    void sendMessage(content, () => {
      if (!shouldPlayInitialParticleTransition) return;

      initialParticleTransitionPendingRef.current = false;
      try {
        const transition = particleTransitionRef.current?.play();
        if (transition) {
          void transition.then(
            () => setShowInitialParticleTransition(false),
            () => setShowInitialParticleTransition(false),
          );
        } else {
          setShowInitialParticleTransition(false);
        }
      } catch {
        setShowInitialParticleTransition(false);
        // A visual transition must never block sending a prompt.
      }
    }).then((result) => {
      if (!result.restoreDraft) return;
      setInitialDraft(content);
      setComposerKey((current) => current + 1);
    });
    return true;
  }

  function handleQuickAction(prompt: string) {
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

  const composerActions = (
    <div className="flex items-center gap-1.5">
      {activeConversationId ? (
        <DataAgentContextUsage
          context={sessionContext}
          loading={sessionContextLoading}
          error={sessionContextError}
          hasSession
        />
      ) : null}
      {modelSelector}
    </div>
  );

  return (
    <>
      {showInitialParticleTransition ? (
        <MagicPatternsParticleTransition
          ref={particleTransitionRef}
          fromRef={particleSourceRef}
          toRef={particleTargetRef}
        />
      ) : null}
      {isLoadingOverlayVisible ? (
        <DataAgentLoading sidebarWidth={sidebarWidth} exiting={loadingOverlayExiting} />
      ) : null}
      <div
        aria-hidden={isLoadingOverlayVisible}
        ref={(element) => {
          if (element) element.inert = isLoadingOverlayVisible;
        }}
        className={
          (initialLoadComplete ? "data-agent-workspace-enter " : "") +
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
              onRenameConversation={renameConversation}
              onDeleteConversation={async (id) => {
                await deleteConversation(id);
                if (id === sessionId) onNewConversation();
              }}
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

        <div className="relative flex min-h-0 min-w-0 flex-1 overflow-hidden">
          {showInitialParticleTransition ? (
            <div
              ref={particleTargetRef}
              className="data-agent-particle-destination"
              aria-hidden="true"
            >
              <div className="data-agent-particle-destination__rail" />
            </div>
          ) : null}
          {isRestoringSession ? (
            <main className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-white ml-2 max-md:ml-0">
              <DataAgentSessionLoading />
            </main>
          ) : !hasMessages ? (
            <main className="flex min-h-0 min-w-0 flex-1 bg-[#f0f0f0] p-2 max-md:p-0">
              <section className="relative flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-[#e7e7e4] bg-white max-md:rounded-none max-md:border-0">
                <div className="pointer-events-none absolute inset-x-0 top-0 z-30 flex items-center justify-between px-[7px] pt-2">
                  <button
                    type="button"
                    aria-label={sidebarOpen ? "收起侧边栏" : "展开侧边栏"}
                    aria-expanded={sidebarOpen}
                    title={sidebarOpen ? "收起侧边栏" : "展开侧边栏"}
                    className="pointer-events-auto flex size-7 cursor-pointer items-center justify-center rounded-lg border border-[#e1e5e4] bg-white text-[#66716f] shadow-[0_1px_2px_rgba(0,0,0,0.035)] transition-colors hover:border-[#cbd2d1] hover:bg-[#f8f9f8] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25"
                    onClick={() => setSidebarOpen((open) => !open)}
                  >
                    <PanelLeft size={15} strokeWidth={1.5} />
                  </button>
                </div>

                <DataAgentEmptyState
                  connectionError={connectionError}
                  sessionNotice={sessionNotice}
                  restored={Boolean(activeConversation?.restored)}
                  composerKey={composerKey}
                  initialDraft={initialDraft}
                  isSending={isSending}
                  onSubmit={handlePromptSubmit}
                  onStop={stopCurrentTurn}
                  onQuickAction={handleQuickAction}
                  trailingActions={composerActions}
                  transitionSourceRef={
                    showInitialParticleTransition ? particleSourceRef : undefined
                  }
                />
              </section>
            </main>
          ) : (
            <main className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-white ml-2 max-md:ml-0">
              <DataAgentSessionWorkspace
                sessionId={sessionId ?? activeConversationId ?? "new"}
                title={activeConversation?.title ?? "新对话"}
                isSending={isSending}
                isReconnecting={isReconnecting}
                sessionUsage={sessionUsage}
                sessionNotice={sessionNotice}
                onStop={stopCurrentTurn}
                sidebarOpen={sidebarOpen}
                onToggleSidebar={() => setSidebarOpen((open) => !open)}
                messages={messages}
                pendingConfirmation={pendingConfirmation}
                onResolveConfirmation={resolveConfirmation}
                scrollRef={messageScrollRef}
                connectionError={connectionError}
                composerKey={composerKey}
                initialDraft={initialDraft}
                onSubmit={handlePromptSubmit}
                trailingActions={composerActions}
              />
            </main>
          )}
        </div>
      </div>
    </>
  );
}

export default DataAgentWorkspace;
