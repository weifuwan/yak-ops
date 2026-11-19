import { useEffect, useRef, useState } from "react";
import {
  Check,
  Copy,
  MoreHorizontal,
  RotateCcw,
  ThumbsDown,
  ThumbsUp,
} from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  toast,
} from "@yak-ops/yak-ui";

import type { AgentMessage } from "../../../model/types";
import { formatConversationTime } from "../../../utils/message-formatters";

type MessageFeedback = "positive" | "negative";

type MessageActionsProps = {
  message: AgentMessage;
  align?: "left" | "right";
  showActions?: boolean;
  onRegenerate?: (message: AgentMessage) => void;
  onFeedback?: (
    messageId: string,
    feedback: MessageFeedback | null,
  ) => void;
};

const actionButtonClassName =
  "inline-flex size-6 shrink-0 cursor-pointer items-center justify-center rounded-md text-[#9a9a9a] transition-colors duration-150 hover:bg-[#f4f4f2] hover:text-[#555555] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25 disabled:cursor-not-allowed disabled:opacity-35";

const copyIconTransitionClassName =
  "absolute transition-[opacity,transform] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]";

export function MessageActions({
  message,
  align = "left",
  showActions = true,
  onRegenerate,
  onFeedback,
}: MessageActionsProps) {
  const [feedback, setFeedback] = useState<MessageFeedback | null>(null);
  const [messageCopied, setMessageCopied] = useState(false);
  const copyResetTimerRef = useRef<number | null>(null);
  const canInteract =
    message.status !== "streaming" &&
    message.status !== "awaiting-confirmation";
  const showToolbar = showActions && canInteract;

  useEffect(
    () => () => {
      if (copyResetTimerRef.current !== null) {
        window.clearTimeout(copyResetTimerRef.current);
      }
    },
    [],
  );

  async function copyText(
    value: string,
    successMessage: string,
    onSuccess?: () => void,
  ) {
    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error("Clipboard API unavailable");
      }
      await navigator.clipboard.writeText(value);
      onSuccess?.();
      toast.success(successMessage);
    } catch {
      toast.error("复制失败，请检查浏览器剪贴板权限");
    }
  }

  function handleMessageCopy() {
    void copyText(message.content, "消息已复制", () => {
      setMessageCopied(true);

      if (copyResetTimerRef.current !== null) {
        window.clearTimeout(copyResetTimerRef.current);
      }

      copyResetTimerRef.current = window.setTimeout(() => {
        setMessageCopied(false);
        copyResetTimerRef.current = null;
      }, 1500);
    });
  }

  function handleFeedback(nextFeedback: MessageFeedback) {
    const next = feedback === nextFeedback ? null : nextFeedback;
    setFeedback(next);
    onFeedback?.(message.id, next);
  }

  return (
    <div
      className={
        "mt-1 flex min-h-6 items-center gap-3 " +
        (align === "right" ? "justify-end" : "justify-start")
      }
    >
      {showToolbar ? (
        <div className="flex items-center gap-1">
          <button
            type="button"
            aria-label="重新生成"
            title={onRegenerate ? "重新生成" : "重新生成（暂未接入）"}
            disabled={!onRegenerate}
            className={actionButtonClassName}
            onClick={() => onRegenerate?.(message)}
          >
            <RotateCcw size={14} strokeWidth={1.8} />
          </button>

          <button
            type="button"
            aria-label="有帮助"
            aria-pressed={feedback === "positive"}
            title="有帮助"
            className={
              actionButtonClassName +
              (feedback === "positive"
                ? " bg-[#fff1f4] text-[#fe2c55] hover:bg-[#fff1f4] hover:text-[#fe2c55]"
                : "")
            }
            onClick={() => handleFeedback("positive")}
          >
            <ThumbsUp size={14} strokeWidth={1.8} />
          </button>

          <button
            type="button"
            aria-label="没有帮助"
            aria-pressed={feedback === "negative"}
            title="没有帮助"
            className={
              actionButtonClassName +
              (feedback === "negative"
                ? " bg-[#fff1f4] text-[#fe2c55] hover:bg-[#fff1f4] hover:text-[#fe2c55]"
                : "")
            }
            onClick={() => handleFeedback("negative")}
          >
            <ThumbsDown size={14} strokeWidth={1.8} />
          </button>

          <button
            type="button"
            aria-label={messageCopied ? "消息已复制" : "复制消息"}
            title={messageCopied ? "已复制" : "复制消息"}
            disabled={!message.content.trim()}
            className={actionButtonClassName}
            onClick={handleMessageCopy}
          >
            <span className="relative flex size-4 items-center justify-center">
              <Copy
                size={14}
                strokeWidth={1.8}
                className={
                  copyIconTransitionClassName +
                  (messageCopied
                    ? " scale-75 rotate-[-10deg] opacity-0"
                    : " scale-100 rotate-0 opacity-100")
                }
              />
              <Check
                size={14}
                strokeWidth={2}
                className={
                  copyIconTransitionClassName +
                  (messageCopied
                    ? " scale-100 rotate-0 text-[#16a36a] opacity-100"
                    : " scale-75 rotate-[10deg] opacity-0")
                }
              />
            </span>
          </button>

          <DropdownMenu>
            <DropdownMenuTrigger
              aria-label="更多消息操作"
              title="更多消息操作"
              className={actionButtonClassName}
            >
              <MoreHorizontal size={15} strokeWidth={1.8} />
            </DropdownMenuTrigger>
            <DropdownMenuContent
              side="bottom"
              align="start"
              sideOffset={6}
              className="w-40 rounded-[10px] border border-[#e8e8e5] bg-white p-1 text-[#333333] shadow-[0_8px_24px_rgba(0,0,0,0.1)]"
            >
              <DropdownMenuItem
                className="min-h-8 cursor-pointer rounded-md px-2.5 text-xs data-[highlighted]:bg-[#f5f5f3]"
                onClick={() =>
                  void copyText(message.id, "消息 ID 已复制")
                }
              >
                复制消息 ID
              </DropdownMenuItem>
              <DropdownMenuItem
                className="min-h-8 cursor-pointer rounded-md px-2.5 text-xs data-[highlighted]:bg-[#f5f5f3]"
                onClick={() =>
                  void copyText(message.createdAt, "消息时间已复制")
                }
              >
                复制消息时间
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      ) : null}

      <time
        dateTime={message.createdAt}
        className="text-[11px] leading-6 text-[#aaaaaa]"
      >
        {formatConversationTime(message.createdAt)}
      </time>
    </div>
  );
}
