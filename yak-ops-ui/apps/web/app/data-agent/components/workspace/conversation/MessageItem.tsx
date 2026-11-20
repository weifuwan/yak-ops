import type { AgentMessage, PendingConfirmation } from "../../../model/types";
import { AssistantMessage } from "./AssistantMessage";
import { MessageActions } from "./MessageActions";
import { UserMessage } from "./UserMessage";

type MessageItemProps = {
  message: AgentMessage;
  pendingConfirmation: PendingConfirmation | null;
  onResolveConfirmation: (approved: boolean) => void;
  selectedActivityId?: string | null;
  onSelectActivity?: (activityId: string) => void;
};

export function MessageItem({
  message,
  pendingConfirmation,
  onResolveConfirmation,
  selectedActivityId,
  onSelectActivity,
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
            selectedActivityId={selectedActivityId}
            onSelectActivity={onSelectActivity}
          />
        )}
        <MessageActions
          message={message}
          align={isUser ? "right" : "left"}
          showActions={!isUser}
        />
      </div>
    </article>
  );
}
