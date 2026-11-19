import type { AgentMessage, PendingConfirmation } from "../../../model/types";
import { formatConversationTime } from "../../../utils/message-formatters";
import { AssistantMessage } from "./AssistantMessage";
import { UserMessage } from "./UserMessage";

type MessageItemProps = {
  message: AgentMessage;
  pendingConfirmation: PendingConfirmation | null;
  onResolveConfirmation: (approved: boolean) => void;
};

export function MessageItem({
  message,
  pendingConfirmation,
  onResolveConfirmation,
}: MessageItemProps) {
  const isUser = message.role === "user";

  return (
    <article className={"flex w-full " + (isUser ? "justify-end" : "justify-start")}>
      <div className={isUser ? "max-w-[82%]" : "w-full max-w-[92%]"}>
        {isUser ? (
          <UserMessage content={message.content} />
        ) : (
          <AssistantMessage
            message={message}
            pendingConfirmation={pendingConfirmation}
            onResolveConfirmation={onResolveConfirmation}
          />
        )}
        <time
          dateTime={message.createdAt}
          className={
            "mt-1 block text-[11px] text-[#aaaaaa] " +
            (isUser ? "text-right" : "text-left")
          }
        >
          {formatConversationTime(message.createdAt)}
        </time>
      </div>
    </article>
  );
}
