import { AlertCircle } from "lucide-react";

import "./data-agent-empty-state.css";
import type { ReactNode, RefObject } from "react";

import { DataAgentPromptComposer } from "../DataAgentPromptComposer";
import { QUICK_ACTIONS } from "./quick-actions";

type DataAgentEmptyStateProps = {
  connectionError: string;
  sessionNotice: string;
  restored: boolean;
  composerKey: number;
  initialDraft: string;
  isSending: boolean;
  onSubmit: (content: string) => boolean | void;
  onStop: () => void;
  onQuickAction: (prompt: string) => void;
  trailingActions: ReactNode;
  transitionSourceRef?: RefObject<HTMLDivElement>;
};

export function DataAgentEmptyState({
  connectionError,
  sessionNotice,
  restored,
  composerKey,
  initialDraft,
  isSending,
  onSubmit,
  onStop,
  onQuickAction,
  trailingActions,
  transitionSourceRef,
}: DataAgentEmptyStateProps) {
  return (
    <div className="data-agent-empty-state relative isolate flex min-h-0 flex-1 overflow-hidden bg-white">
      <div className="data-agent-empty-state__wave" aria-hidden="true" />
      <div className="data-agent-empty-state__content flex min-h-0 min-w-0 flex-1 flex-col items-center justify-center overflow-y-auto px-6 py-10">
        <div className="w-full max-w-[540px]">
          {connectionError ? (
            <div
              role="alert"
              className="mb-3 flex items-start gap-2 rounded-xl border border-[#f0d6d6] bg-[#fff7f7] px-3 py-2.5 text-xs leading-5 text-[#a14d4d]"
            >
              <AlertCircle size={15} className="mt-0.5 shrink-0" />
              <span>{connectionError}</span>
            </div>
          ) : null}

          {restored ? (
            <div className="mb-3 rounded-xl border border-[#e7e9e8] bg-[#f8faf9] px-3 py-2 text-xs leading-5 text-[#78817f]">
              已恢复服务端历史消息，可以继续对话。
            </div>
          ) : null}
          {sessionNotice ? (
            <div className="mb-3 rounded-xl border border-[#f0e3c7] bg-[#fffaf0] px-3 py-2 text-xs leading-5 text-[#8a6a32]">
              {sessionNotice}
            </div>
          ) : null}

          <DataAgentPromptComposer
            key={composerKey}
            initialValue={initialDraft}
            onSubmit={onSubmit}
            isSubmitting={isSending}
            onStop={onStop}
            trailingActions={trailingActions}
            transitionSourceRef={transitionSourceRef}
            placeholder="你想让 Data Agent 帮你完成什么？"
          />

          <div className="mt-3 grid grid-cols-2 gap-2">
            {QUICK_ACTIONS.map((action) => {
              const Icon = action.Icon;
              return (
                <button
                  key={action.id}
                  type="button"
                  disabled={isSending}
                  className="flex h-10 min-w-0 cursor-pointer items-center gap-2 rounded-[12px] border border-[#e2e6e5] bg-white px-2.5 text-left text-[13px] font-medium text-[#26302f] transition-[border-color,background-color] duration-150 hover:border-[#cbd4d2] hover:bg-[#fafbfa] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25 disabled:cursor-not-allowed disabled:opacity-50"
                  onClick={() => onQuickAction(action.prompt)}
                >
                  <span className={"flex size-5 shrink-0 items-center justify-center rounded-md " + action.iconClass}>
                    <Icon size={12} strokeWidth={1.8} />
                  </span>
                  <span className="min-w-0 flex-1 truncate">{action.label}</span>
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
