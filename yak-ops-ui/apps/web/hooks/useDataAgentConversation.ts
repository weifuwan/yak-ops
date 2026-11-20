import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { handleAuthenticationFailure } from "@/service/http/request";
import {
  createAgentSession,
  DataAgentApiError,
  listAgentSessions,
  sendAgentMessage,
  type AgentStreamEvent,
} from "@/service/data-agent";

import {
  conversationReducer,
  initialConversationState,
} from "../app/data-agent/model/conversation-reducer";
import type {
  AgentConversation,
  PendingConfirmation,
} from "../app/data-agent/model/types";
import {
  agentErrorMessage,
  conversationTitle,
  formatSessionTime,
} from "../app/data-agent/utils/message-formatters";

type SendMessageResult = {
  restoreDraft: boolean;
};

export function useDataAgentConversation() {
  const { currentUser } = useAuth();
  const { currentWorkspace, loading: isWorkspaceLoading } = useWorkspace();
  const requestIdentity = useMemo(
    () =>
      currentUser?.id && currentWorkspace?.id
        ? {
            userId: String(currentUser.id),
            workspaceId: String(currentWorkspace.id),
          }
        : null,
    [currentUser?.id, currentWorkspace?.id],
  );

  const [state, dispatch] = useReducer(
    conversationReducer,
    initialConversationState,
  );
  const [isSending, setIsSending] = useState(false);
  const [restoredIdentityKey, setRestoredIdentityKey] = useState<string | null>(null);
  const requestIdentityKey = requestIdentity
    ? requestIdentity.userId + ":" + requestIdentity.workspaceId
    : null;
  const [connectionError, setConnectionError] = useState("");
  const [pendingConfirmation, setPendingConfirmation] =
    useState<PendingConfirmation | null>(null);

  const sendingRef = useRef(false);
  const streamControllerRef = useRef<AbortController | null>(null);
  const pendingConfirmationRef = useRef<PendingConfirmation | null>(null);
  const pendingConfirmationResolverRef = useRef<((approved: boolean) => void) | null>(null);

  const activeConversation = useMemo(
    () =>
      state.conversations.find(
        (conversation) => conversation.id === state.activeConversationId,
      ) ?? null,
    [state.activeConversationId, state.conversations],
  );
  const messages = activeConversation?.messages ?? [];
  const lastMessage = messages[messages.length - 1];

  useEffect(() => {
    const controller = new AbortController();

    // A workspace or login change invalidates the old conversation context.
    streamControllerRef.current?.abort();
    pendingConfirmationResolverRef.current?.(false);
    pendingConfirmationResolverRef.current = null;
    pendingConfirmationRef.current = null;
    setPendingConfirmation(null);
    setConnectionError("");
    dispatch({ type: "RESET" });

    if (!requestIdentity) {
      return () => controller.abort();
    }

    listAgentSessions(requestIdentity, controller.signal)
      .then((sessions) => {
        if (controller.signal.aborted) return;
        const restored: AgentConversation[] = sessions
          .filter((session) => Boolean(session.sessionId))
          .map((session) => ({
            id: session.sessionId,
            title: "历史会话 · " + session.sessionId.slice(0, 6),
            updatedAt: formatSessionTime(session.updatedAt),
            messages: [],
            restored: true,
          }));
        dispatch({ type: "RESTORE_CONVERSATIONS", conversations: restored });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof DataAgentApiError && error.status === 401) {
          handleAuthenticationFailure(
            "Data Agent 无法验证当前 Yak-OPS 登录状态，请重新登录。",
          );
          return;
        }
        setConnectionError(agentErrorMessage(error));
      })
      .finally(() => {
        if (!controller.signal.aborted && requestIdentityKey) {
          setRestoredIdentityKey(requestIdentityKey);
        }
      });

    return () => controller.abort();
  }, [requestIdentity, requestIdentityKey]);

  useEffect(
    () => () => {
      streamControllerRef.current?.abort();
      pendingConfirmationResolverRef.current?.(false);
      pendingConfirmationResolverRef.current = null;
      pendingConfirmationRef.current = null;
    },
    [],
  );

  const createConversation = useCallback(() => {
    if (sendingRef.current) return;
    dispatch({ type: "SELECT_CONVERSATION", sessionId: null });
    setConnectionError("");
  }, []);

  const selectConversation = useCallback((sessionId: string) => {
    if (sendingRef.current) return;
    dispatch({ type: "SELECT_CONVERSATION", sessionId });
    setConnectionError("");
  }, []);

  const resolveConfirmation = useCallback((approved: boolean) => {
    const pending = pendingConfirmationRef.current;
    if (!pending) return;

    pendingConfirmationRef.current = null;
    setPendingConfirmation(null);
    dispatch({
      type: "CONFIRMATION_RESOLVED",
      sessionId: pending.sessionId,
      messageId: pending.assistantMessageId,
      approved,
      updatedAt: new Date().toISOString(),
    });

    const resolve = pendingConfirmationResolverRef.current;
    pendingConfirmationResolverRef.current = null;
    resolve?.(approved);
  }, []);

  const sendMessage = useCallback(
    async (content: string): Promise<SendMessageResult> => {
      if (sendingRef.current) return { restoreDraft: false };
      if (!requestIdentity) {
        setConnectionError("请先登录并选择工作空间，再使用 Data Agent。");
        return { restoreDraft: true };
      }

      sendingRef.current = true;
      setIsSending(true);
      setConnectionError("");

      const controller = new AbortController();
      streamControllerRef.current = controller;
      const now = new Date().toISOString();
      const userMessage = {
        id: globalThis.crypto.randomUUID(),
        role: "user" as const,
        content,
        createdAt: now,
      };
      const assistantMessage = {
        id: globalThis.crypto.randomUUID(),
        role: "assistant" as const,
        content: "",
        createdAt: now,
        status: "streaming" as const,
        toolCalls: [],
        activities: [],
      };

      let sessionId = state.activeConversationId;

      try {
        if (!sessionId) {
          const created = await createAgentSession(requestIdentity, controller.signal);
          sessionId = created.sessionId;
        }

        const resolvedSessionId = sessionId;
        dispatch({
          type: "MESSAGE_PAIR_ADDED",
          sessionId: resolvedSessionId,
          title: conversationTitle(content),
          updatedAt: now,
          userMessage,
          assistantMessage,
        });

        await sendAgentMessage(resolvedSessionId, content, {
          identity: requestIdentity,
          signal: controller.signal,
          onEvent: (event: AgentStreamEvent) => {
            const updatedAt = new Date().toISOString();
            switch (event.type) {
              case "text_delta":
                dispatch({
                  type: "TEXT_DELTA",
                  sessionId: resolvedSessionId,
                  messageId: assistantMessage.id,
                  delta: event.delta,
                  replyId: event.replyId,
                  blockId: event.blockId,
                  updatedAt,
                });
                break;
              case "thinking_start":
                dispatch({
                  type: "THINKING_STARTED",
                  sessionId: resolvedSessionId,
                  messageId: assistantMessage.id,
                  replyId: event.replyId,
                  blockId: event.blockId,
                  updatedAt,
                });
                break;
              case "thinking_delta":
                // Raw model reasoning is intentionally not persisted or rendered.
                break;
              case "thinking_end":
                dispatch({
                  type: "THINKING_COMPLETED",
                  sessionId: resolvedSessionId,
                  messageId: assistantMessage.id,
                  replyId: event.replyId,
                  blockId: event.blockId,
                  updatedAt,
                });
                break;
              case "tool_start":
                dispatch({
                  type: "TOOL_STARTED",
                  sessionId: resolvedSessionId,
                  messageId: assistantMessage.id,
                  toolCallId: event.toolCallId,
                  toolName: event.tool,
                  replyId: event.replyId,
                  updatedAt,
                });
                break;
              case "tool_call_delta":
                dispatch({
                  type: "TOOL_INPUT_DELTA",
                  sessionId: resolvedSessionId,
                  messageId: assistantMessage.id,
                  toolCallId: event.toolCallId,
                  delta: event.delta,
                  updatedAt,
                });
                break;
              case "tool_result_delta":
                dispatch({
                  type: "TOOL_OUTPUT_DELTA",
                  sessionId: resolvedSessionId,
                  messageId: assistantMessage.id,
                  toolCallId: event.toolCallId,
                  delta: event.delta,
                  updatedAt,
                });
                break;
              case "tool_result":
                dispatch({
                  type: "TOOL_COMPLETED",
                  sessionId: resolvedSessionId,
                  messageId: assistantMessage.id,
                  toolCallId: event.toolCallId,
                  state: event.state,
                  updatedAt,
                });
                break;
              case "progress":
                dispatch({
                  type: "PROGRESS_UPDATED",
                  sessionId: resolvedSessionId,
                  messageId: assistantMessage.id,
                  phase: event.phase,
                  title: event.title,
                  status: event.status,
                  replyId: event.replyId,
                  updatedAt,
                });
                break;
              case "usage":
                dispatch({
                  type: "USAGE_RECORDED",
                  sessionId: resolvedSessionId,
                  messageId: assistantMessage.id,
                  usage: {
                    replyId: event.replyId,
                    inputTokens: event.inputTokens,
                    outputTokens: event.outputTokens,
                    cacheInputTokens: event.cacheInputTokens,
                    cacheCreationInputTokens: event.cacheCreationInputTokens,
                  },
                  updatedAt,
                });
                break;
              case "reply_end":
                dispatch({
                  type: "REPLY_ENDED",
                  sessionId: resolvedSessionId,
                  messageId: assistantMessage.id,
                  replyId: event.replyId,
                  finishedReason: event.finishedReason,
                  error: event.error,
                  updatedAt,
                });
                break;
              case "done":
                dispatch({
                  type: "MESSAGE_COMPLETED",
                  sessionId: resolvedSessionId,
                  messageId: assistantMessage.id,
                  updatedAt,
                });
                break;
              case "error":
                dispatch({
                  type: "MESSAGE_FAILED",
                  sessionId: resolvedSessionId,
                  messageId: assistantMessage.id,
                  error: event.message,
                  updatedAt,
                });
                break;
            }
          },
          onConfirmRequired: (event) =>
            new Promise<boolean>((resolve) => {
              const pending = {
                sessionId: resolvedSessionId,
                assistantMessageId: assistantMessage.id,
                event,
              };
              pendingConfirmationRef.current = pending;
              pendingConfirmationResolverRef.current = resolve;
              setPendingConfirmation(pending);
              dispatch({
                type: "CONFIRMATION_REQUIRED",
                sessionId: resolvedSessionId,
                messageId: assistantMessage.id,
                toolCalls: event.toolCalls.map((toolCall) => ({
                  ...toolCall,
                  status: "awaiting-confirmation" as const,
                })),
                updatedAt: new Date().toISOString(),
              });
            }),
        });

        return { restoreDraft: false };
      } catch (error) {
        if (!controller.signal.aborted) {
          if (error instanceof DataAgentApiError && error.status === 401) {
            handleAuthenticationFailure(
              "Data Agent 无法验证当前 Yak-OPS 登录状态，请重新登录。",
            );
          }

          const message = agentErrorMessage(error);
          if (sessionId) {
            dispatch({
              type: "MESSAGE_FAILED",
              sessionId,
              messageId: assistantMessage.id,
              error: message,
              updatedAt: new Date().toISOString(),
            });
          } else {
            setConnectionError(message);
          }
          return { restoreDraft: !sessionId };
        }

        return { restoreDraft: false };
      } finally {
        if (streamControllerRef.current === controller) {
          streamControllerRef.current = null;
        }
        sendingRef.current = false;
        setIsSending(false);
      }
    },
    [requestIdentity, state.activeConversationId],
  );

  return {
    conversations: state.conversations,
    activeConversationId: state.activeConversationId,
    activeConversation,
    messages,
    lastMessage,
    isSending,
    isLoading: isWorkspaceLoading || Boolean(requestIdentityKey && restoredIdentityKey !== requestIdentityKey),
    connectionError,
    pendingConfirmation,
    createConversation,
    selectConversation,
    sendMessage,
    resolveConfirmation,
  };
}
