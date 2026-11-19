import { LoaderCircle } from "lucide-react";

import { DataAgentMarkdown } from "../../markdown/DataAgentMarkdown";
import type { AgentMessage, PendingConfirmation } from "../../../model/types";
import { ConfirmationCard } from "./ConfirmationCard";
import { MessageError } from "./MessageError";
import { ToolCallList } from "./ToolCallList";

type AssistantMessageProps = {
  message: AgentMessage;
  pendingConfirmation: PendingConfirmation | null;
  onResolveConfirmation: (approved: boolean) => void;
};

export function AssistantMessage({
  message,
  pendingConfirmation,
  onResolveConfirmation,
}: AssistantMessageProps) {
  const toolCalls = message.toolCalls ?? [];

  return (
    <div className="min-w-0 text-[14px] leading-7 text-[#27302f]">
      {message.content.trim() ? (
        <DataAgentMarkdown
          content={message.content}
          isStreaming={message.status === "streaming"}
        />
      ) : message.status === "streaming" && toolCalls.length === 0 ? (
        <div className="flex items-center gap-2 text-xs text-[#899391]">
          <LoaderCircle size={14} className="animate-spin" />
          正在思考...
        </div>
      ) : null}

      {toolCalls.length > 0 ? <ToolCallList toolCalls={toolCalls} /> : null}

      {pendingConfirmation?.assistantMessageId === message.id ? (
        <ConfirmationCard
          event={pendingConfirmation.event}
          onResolve={onResolveConfirmation}
        />
      ) : null}

      {message.error ? <MessageError message={message.error} /> : null}
    </div>
  );
}
