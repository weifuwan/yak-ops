import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { handleAuthenticationFailure } from "@/service/http/request";
import {
  confirmAgentSession,
  createAgentSession,
  DataAgentApiError,
  deleteAgentSession,
  getAgentSessionMessages,
  getAgentSessionStatus,
  interruptAgentSession,
  listAgentSessions,
  renameAgentSession,
  resumeAgentStream,
  sendAgentMessage,
  type AgentSessionStatus,
  type AgentSessionSummary,
  type AgentSessionUsage,
  type AgentStreamEvent,
} from "@/service/data-agent";

import {
  conversationReducer,
  initialConversationState,
} from "../app/data-agent/model/conversation-reducer";
import type {
  AgentConversation,
  AgentMessage,
  PendingConfirmation,
} from "../app/data-agent/model/types";
import { mapAgentHistoryToConversation } from "../app/data-agent/model/session-history";
import {
  agentErrorMessage,
  conversationTitle,
  formatSessionTime,
} from "../app/data-agent/utils/message-formatters";

type SendMessageResult = {
  restoreDraft: boolean;
};

type UseDataAgentConversationOptions = {
  sessionId: string | null;
  onSessionCreated?: (sessionId: string) => void;
};

const EMPTY_USAGE: AgentSessionUsage = {
  inputTokens: 0,
  outputTokens: 0,
  cacheInputTokens: 0,
  cacheCreationInputTokens: 0,
};

function normalizeUsage(value?: AgentSessionUsage | null): AgentSessionUsage {
  return {
    inputTokens: Math.max(0, Number(value?.inputTokens) || 0),
    outputTokens: Math.max(0, Number(value?.outputTokens) || 0),
    cacheInputTokens: Math.max(0, Number(value?.cacheInputTokens) || 0),
    cacheCreationInputTokens: Math.max(0, Number(value?.cacheCreationInputTokens) || 0),
  };
}

function appendActiveTurn(
  conversation: AgentConversation,
  status: AgentSessionStatus,
  preferredAssistantId?: string,
) {
  const activeText = status.activeTurnMessage?.trim() ?? "";
  if (!status.busy && !status.stale) {
    return { conversation, assistantMessageId: preferredAssistantId ?? null };
  }

  const turnId = status.turnId || conversation.id;
  const assistantId = preferredAssistantId || "turn:" + turnId;
  const messages = [...conversation.messages];
  let lastUserIndex = -1;
  messages.forEach((message, index) => {
    if (message.role === "user") lastUserIndex = index;
  });
  const lastUser = lastUserIndex >= 0 ? messages[lastUserIndex] : null;
  const activeUserAlreadyRestored =
    Boolean(activeText) &&
    lastUser?.content === activeText &&
    lastUserIndex >= messages.length - 2;

  if (!activeUserAlreadyRestored && activeText) {
    messages.push({
      id: "turn-user:" + turnId,
      role: "user",
      content: activeText,
      createdAt: status.startedAt || new Date().toISOString(),
      status: "complete",
    });
  }

  let resolvedAssistantId = assistantId;
  const lastMessage = messages[messages.length - 1];
  if (activeUserAlreadyRestored && lastMessage?.role === "assistant") {
    resolvedAssistantId = preferredAssistantId || lastMessage.id;
  } else {
    messages.push({
      id: assistantId,
      role: "assistant",
      content: status.stale
        ? "上一轮执行因 Agent 服务重启而中断，已恢复此前保存的对话记录。"
        : "",
      createdAt: status.startedAt || new Date().toISOString(),
      status: status.stale ? "interrupted" : "streaming",
      toolCalls: [],
      activities: [],
    });
  }

  return {
    conversation: { ...conversation, messages },
    assistantMessageId: resolvedAssistantId,
  };
}

function createSessionSummary(
  session: AgentSessionSummary,
): AgentConversation {
  return {
    id: session.sessionId,
    title: session.title || "历史会话 · " + session.sessionId.slice(0, 6),
    preview: session.preview || "",
    updatedAt: formatSessionTime(session.updatedAt),
    messages: [],
    restored: true,
  };
}

export function useDataAgentConversation({
  sessionId,
  onSessionCreated,
}: UseDataAgentConversationOptions) {
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
  const [isReconnecting, setIsReconnecting] = useState(false);
  const [isLoadingSessions, setIsLoadingSessions] = useState(true);
  const [isRestoringSession, setIsRestoringSession] = useState(false);
  const [restoredIdentityKey, setRestoredIdentityKey] = useState<string | null>(null);
  const [connectionError, setConnectionError] = useState("");
  const [sessionNotice, setSessionNotice] = useState("");
  const [sessionUsage, setSessionUsage] = useState<AgentSessionUsage>(EMPTY_USAGE);
  const [pendingConfirmation, setPendingConfirmation] =
    useState<PendingConfirmation | null>(null);

  const requestIdentityKey = requestIdentity
    ? requestIdentity.userId + ":" + requestIdentity.workspaceId
    : null;

  const sendingRef = useRef(false);
  const streamControllerRef = useRef<AbortController | null>(null);
  const streamSessionIdRef = useRef<string | null>(null);
  const turnIdRef = useRef<string | null>(null);
  const lastEventCursorRef = useRef(0);
  const pendingConfirmationRef = useRef<PendingConfirmation | null>(null);
  const stateRef = useRef(state);
  const onSessionCreatedRef = useRef(onSessionCreated);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  useEffect(() => {
    onSessionCreatedRef.current = onSessionCreated;
  }, [onSessionCreated]);

  const activeConversation = useMemo(
    () =>
      state.conversations.find(
        (conversation) => conversation.id === (sessionId ?? state.activeConversationId),
      ) ?? null,
    [sessionId, state.activeConversationId, state.conversations],
  );
  const activeConversationId = sessionId ?? state.activeConversationId;
  const messages = activeConversation?.messages ?? [];
  const lastMessage = messages[messages.length - 1];

  const setPending = useCallback((pending: PendingConfirmation | null) => {
    pendingConfirmationRef.current = pending;
    setPendingConfirmation(pending);
  }, []);

  const restoreSessionSnapshot = useCallback(
    async (
      targetSessionId: string,
      signal: AbortSignal,
      preferredAssistantId?: string,
    ) => {
      if (!requestIdentity) throw new Error("请先登录并选择工作空间");

      const [status, history] = await Promise.all([
        getAgentSessionStatus(targetSessionId, requestIdentity, signal),
        getAgentSessionMessages(targetSessionId, requestIdentity, signal),
      ]);
      if (signal.aborted) return { status, assistantMessageId: null };

      const existing = stateRef.current.conversations.find(
        (item) => item.id === targetSessionId,
      );
      const summary = {
        ...status,
        title: status.title || existing?.title || "历史会话 · " + targetSessionId.slice(0, 6),
        preview: status.preview || existing?.preview || "",
      };
      let conversation = mapAgentHistoryToConversation(targetSessionId, summary, history);
      const activeTurn = appendActiveTurn(
        conversation,
        status,
        preferredAssistantId,
      );
      conversation = activeTurn.conversation;

      dispatch({ type: "HYDRATE_CONVERSATION", conversation });
      setSessionUsage(normalizeUsage(status.usage ?? history.usage));

      if (status.stale) {
        setSessionNotice("上一轮执行因 Agent 服务重启而中断，已恢复此前保存的对话记录。");
      } else if (history.truncated) {
        setSessionNotice("历史消息较多，当前展示最近一段对话记录。");
      } else {
        setSessionNotice("");
      }

      turnIdRef.current = status.turnId || null;

      if (status.pendingConfirmation && activeTurn.assistantMessageId) {
        const event = {
          type: "confirm_required" as const,
          replyId: status.pendingConfirmation.replyId,
          toolCalls: status.pendingConfirmation.toolCalls,
        };
        setPending({
          sessionId: targetSessionId,
          assistantMessageId: activeTurn.assistantMessageId,
          event,
        });
        dispatch({
          type: "CONFIRMATION_REQUIRED",
          sessionId: targetSessionId,
          messageId: activeTurn.assistantMessageId,
          toolCalls: event.toolCalls.map((toolCall) => ({
            ...toolCall,
            status: "awaiting-confirmation" as const,
          })),
          updatedAt: new Date().toISOString(),
        });
      } else {
        setPending(null);
      }

      return {
        status,
        assistantMessageId: activeTurn.assistantMessageId,
      };
    },
    [requestIdentity, setPending],
  );

  const createStreamOptions = useCallback(
    (
      targetSessionId: string,
      assistantMessageId: string,
      signal: AbortSignal,
    ) => ({
      identity: requestIdentity!,
      signal,
      onReconnect: () => setIsReconnecting(true),
      onConfirmRequired: (
        event: Extract<AgentStreamEvent, { type: "confirm_required" }>,
      ) => {
        if (event.turnId) turnIdRef.current = event.turnId;
        const pending = {
          sessionId: targetSessionId,
          assistantMessageId,
          event,
        };
        setPending(pending);
        dispatch({
          type: "CONFIRMATION_REQUIRED",
          sessionId: targetSessionId,
          messageId: assistantMessageId,
          toolCalls: event.toolCalls.map((toolCall) => ({
            ...toolCall,
            status: "awaiting-confirmation" as const,
          })),
          updatedAt: new Date().toISOString(),
        });
      },
      onEvent: async (event: AgentStreamEvent) => {
        if (event.seq) lastEventCursorRef.current = Math.max(lastEventCursorRef.current, event.seq);
        if (event.turnId) turnIdRef.current = event.turnId;
        const updatedAt = event.at || new Date().toISOString();

        switch (event.type) {
          case "replay_reset":
            await restoreSessionSnapshot(targetSessionId, signal, assistantMessageId);
            break;
          case "stream_idle":
            break;
          case "text_delta":
            dispatch({
              type: "TEXT_DELTA",
              sessionId: targetSessionId,
              messageId: assistantMessageId,
              delta: event.delta,
              replyId: event.replyId,
              blockId: event.blockId,
              updatedAt,
            });
            break;
          case "thinking_start":
            dispatch({
              type: "THINKING_STARTED",
              sessionId: targetSessionId,
              messageId: assistantMessageId,
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
              sessionId: targetSessionId,
              messageId: assistantMessageId,
              replyId: event.replyId,
              blockId: event.blockId,
              updatedAt,
            });
            break;
          case "tool_start":
            dispatch({
              type: "TOOL_STARTED",
              sessionId: targetSessionId,
              messageId: assistantMessageId,
              toolCallId: event.toolCallId,
              toolName: event.tool,
              replyId: event.replyId,
              updatedAt,
            });
            break;
          case "tool_call_delta":
            dispatch({
              type: "TOOL_INPUT_DELTA",
              sessionId: targetSessionId,
              messageId: assistantMessageId,
              toolCallId: event.toolCallId,
              delta: event.delta,
              updatedAt,
            });
            break;
          case "tool_result_delta":
            dispatch({
              type: "TOOL_OUTPUT_DELTA",
              sessionId: targetSessionId,
              messageId: assistantMessageId,
              toolCallId: event.toolCallId,
              delta: event.delta,
              updatedAt,
            });
            break;
          case "tool_result":
            dispatch({
              type: "TOOL_COMPLETED",
              sessionId: targetSessionId,
              messageId: assistantMessageId,
              toolCallId: event.toolCallId,
              state: event.state,
              updatedAt,
            });
            break;
          case "progress":
            dispatch({
              type: "PROGRESS_UPDATED",
              sessionId: targetSessionId,
              messageId: assistantMessageId,
              phase: event.phase,
              title: event.title,
              status: event.status,
              replyId: event.replyId,
              updatedAt,
            });
            if (event.phase === "confirm" && event.status === "error") {
              setPending(null);
            }
            break;
          case "usage":
            dispatch({
              type: "USAGE_RECORDED",
              sessionId: targetSessionId,
              messageId: assistantMessageId,
              usage: {
                replyId: event.replyId,
                inputTokens: event.inputTokens,
                outputTokens: event.outputTokens,
                cacheInputTokens: event.cacheInputTokens,
                cacheCreationInputTokens: event.cacheCreationInputTokens,
              },
              updatedAt,
            });
            setSessionUsage((current) => ({
              inputTokens: current.inputTokens + event.inputTokens,
              outputTokens: current.outputTokens + event.outputTokens,
              cacheInputTokens: current.cacheInputTokens + event.cacheInputTokens,
              cacheCreationInputTokens:
                current.cacheCreationInputTokens + event.cacheCreationInputTokens,
            }));
            break;
          case "reply_end":
            dispatch({
              type: "REPLY_ENDED",
              sessionId: targetSessionId,
              messageId: assistantMessageId,
              replyId: event.replyId,
              finishedReason: event.finishedReason,
              error: event.error,
              updatedAt,
            });
            break;
          case "done":
            dispatch({
              type: "MESSAGE_COMPLETED",
              sessionId: targetSessionId,
              messageId: assistantMessageId,
              updatedAt,
              finishedReason: event.finishedReason,
            });
            break;
          case "error":
            dispatch({
              type: "MESSAGE_FAILED",
              sessionId: targetSessionId,
              messageId: assistantMessageId,
              error: event.message,
              updatedAt,
            });
            break;
          case "confirm_required":
            break;
        }
      },
    }),
    [requestIdentity, restoreSessionSnapshot, setPending],
  );

  useEffect(() => {
    const controller = new AbortController();

    streamControllerRef.current?.abort();
    streamControllerRef.current = null;
    streamSessionIdRef.current = null;
    sendingRef.current = false;
    turnIdRef.current = null;
    lastEventCursorRef.current = 0;
    setIsSending(false);
    setIsReconnecting(false);
    setPending(null);
    setConnectionError("");
    setSessionNotice("");
    setSessionUsage(EMPTY_USAGE);
    dispatch({ type: "RESET" });

    if (!requestIdentity) {
      setIsLoadingSessions(false);
      setRestoredIdentityKey(null);
      return () => controller.abort();
    }

    setIsLoadingSessions(true);
    listAgentSessions(requestIdentity, controller.signal)
      .then((sessions) => {
        if (controller.signal.aborted) return;
        dispatch({
          type: "RESTORE_CONVERSATIONS",
          conversations: sessions
            .filter((session) => Boolean(session.sessionId))
            .map(createSessionSummary),
        });
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
          setIsLoadingSessions(false);
        }
      });

    return () => controller.abort();
  }, [requestIdentity, requestIdentityKey, setPending]);

  useEffect(() => {
    if (!requestIdentity || !sessionId) {
      if (streamSessionIdRef.current !== sessionId) {
        streamControllerRef.current?.abort();
        streamControllerRef.current = null;
        streamSessionIdRef.current = null;
        sendingRef.current = false;
        setIsSending(false);
        setIsReconnecting(false);
      }
      dispatch({ type: "SELECT_CONVERSATION", sessionId: null });
      setPending(null);
      setSessionNotice("");
      return;
    }

    dispatch({ type: "SELECT_CONVERSATION", sessionId });

    // The first POST already owns this stream. Updating the URL after session
    // creation must not abort it or start a duplicate subscription.
    if (streamSessionIdRef.current === sessionId && sendingRef.current) {
      return;
    }

    streamControllerRef.current?.abort();
    const controller = new AbortController();
    streamControllerRef.current = controller;
    streamSessionIdRef.current = sessionId;
    lastEventCursorRef.current = 0;
    setIsRestoringSession(true);
    setConnectionError("");

    void (async () => {
      const restored = await restoreSessionSnapshot(sessionId, controller.signal);
      if (controller.signal.aborted) return;

      if (!restored.status.busy) {
        setIsSending(false);
        sendingRef.current = false;
        return;
      }

      const assistantMessageId =
        restored.assistantMessageId || "turn:" + (restored.status.turnId || sessionId);
      sendingRef.current = true;
      setIsSending(true);
      setIsReconnecting(true);

      await resumeAgentStream(
        sessionId,
        0,
        createStreamOptions(sessionId, assistantMessageId, controller.signal),
      );
      if (!controller.signal.aborted) {
        await restoreSessionSnapshot(sessionId, controller.signal, assistantMessageId);
      }
    })()
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof DataAgentApiError && error.status === 401) {
          handleAuthenticationFailure(
            "Data Agent 无法验证当前 Yak-OPS 登录状态，请重新登录。",
          );
        } else {
          setConnectionError(agentErrorMessage(error));
        }
      })
      .finally(() => {
        if (streamControllerRef.current === controller) {
          streamControllerRef.current = null;
        }
        if (!controller.signal.aborted) {
          sendingRef.current = false;
          setIsSending(false);
          setIsReconnecting(false);
        }
        setIsRestoringSession(false);
      });

    return () => {
      controller.abort();
      if (streamControllerRef.current === controller) {
        streamControllerRef.current = null;
      }
    };
  }, [
    sessionId,
    requestIdentity,
    restoreSessionSnapshot,
    createStreamOptions,
    setPending,
  ]);

  useEffect(
    () => () => {
      streamControllerRef.current?.abort();
      streamControllerRef.current = null;
      pendingConfirmationRef.current = null;
    },
    [],
  );

  const createConversation = useCallback(() => {
    if (sendingRef.current) return;
    dispatch({ type: "SELECT_CONVERSATION", sessionId: null });
    setConnectionError("");
    setSessionNotice("");
    setPending(null);
  }, [setPending]);

  const selectConversation = useCallback(
    (targetSessionId: string) => {
      if (sendingRef.current) return;
      dispatch({ type: "SELECT_CONVERSATION", sessionId: targetSessionId });
      setConnectionError("");
      setSessionNotice("");
    },
    [],
  );

  const sendMessage = useCallback(
    async (content: string): Promise<SendMessageResult> => {
      if (sendingRef.current) return { restoreDraft: false };
      if (!requestIdentity) {
        setConnectionError("请先登录并选择工作空间，再使用 Data Agent。");
        return { restoreDraft: true };
      }

      sendingRef.current = true;
      setIsSending(true);
      setIsReconnecting(false);
      setConnectionError("");
      setSessionNotice("");

      const controller = new AbortController();
      streamControllerRef.current = controller;
      let resolvedSessionId = sessionId;
      const now = new Date().toISOString();
      const userMessage: AgentMessage = {
        id: globalThis.crypto.randomUUID(),
        role: "user",
        content,
        createdAt: now,
        status: "complete",
      };
      const assistantMessage: AgentMessage = {
        id: globalThis.crypto.randomUUID(),
        role: "assistant",
        content: "",
        createdAt: now,
        status: "streaming",
        toolCalls: [],
        activities: [],
      };

      try {
        if (!resolvedSessionId) {
          const created = await createAgentSession(requestIdentity, controller.signal);
          resolvedSessionId = created.sessionId;
          streamSessionIdRef.current = resolvedSessionId;
          dispatch({ type: "SELECT_CONVERSATION", sessionId: resolvedSessionId });
          onSessionCreatedRef.current?.(resolvedSessionId);
        }

        const targetSessionId = resolvedSessionId;
        const preview = content.replace(/\s+/g, " ").trim().slice(0, 120);
        dispatch({
          type: "MESSAGE_PAIR_ADDED",
          sessionId: targetSessionId,
          title: conversationTitle(content),
          preview,
          updatedAt: now,
          userMessage,
          assistantMessage,
        });

        await sendAgentMessage(targetSessionId, content, {
          ...createStreamOptions(targetSessionId, assistantMessage.id, controller.signal),
        });

        if (!controller.signal.aborted) {
          await restoreSessionSnapshot(
            targetSessionId,
            controller.signal,
            assistantMessage.id,
          );
        }
        return { restoreDraft: false };
      } catch (error) {
        if (!controller.signal.aborted) {
          if (error instanceof DataAgentApiError && error.status === 401) {
            handleAuthenticationFailure(
              "Data Agent 无法验证当前 Yak-OPS 登录状态，请重新登录。",
            );
          }

          const message = agentErrorMessage(error);
          if (resolvedSessionId) {
            dispatch({
              type: "MESSAGE_FAILED",
              sessionId: resolvedSessionId,
              messageId: assistantMessage.id,
              error: message,
              updatedAt: new Date().toISOString(),
            });
          }
          setConnectionError(message);
          return { restoreDraft: !resolvedSessionId };
        }
        return { restoreDraft: false };
      } finally {
        if (streamControllerRef.current === controller) {
          streamControllerRef.current = null;
        }
        if (!controller.signal.aborted) {
          sendingRef.current = false;
          setIsSending(false);
          setIsReconnecting(false);
        }
      }
    },
    [
      requestIdentity,
      sessionId,
      createStreamOptions,
      restoreSessionSnapshot,
    ],
  );

  const resolveConfirmation = useCallback(
    (approved: boolean) => {
      const pending = pendingConfirmationRef.current;
      if (!pending || !requestIdentity) return;

      const controller = new AbortController();
      void confirmAgentSession(
        pending.sessionId,
        approved,
        requestIdentity,
        controller.signal,
      )
        .then(() => {
          dispatch({
            type: "CONFIRMATION_RESOLVED",
            sessionId: pending.sessionId,
            messageId: pending.assistantMessageId,
            approved,
            updatedAt: new Date().toISOString(),
          });
          setPending(null);
        })
        .catch((error: unknown) => {
          if (error instanceof DataAgentApiError && error.status === 409) {
            setPending(null);
            return;
          }
          setConnectionError(agentErrorMessage(error));
        });
    },
    [requestIdentity, setPending],
  );

  const stopCurrentTurn = useCallback(async () => {
    if (!sessionId || !requestIdentity || !sendingRef.current) return;
    try {
      const result = await interruptAgentSession(
        sessionId,
        turnIdRef.current,
        requestIdentity,
      );
      if (result.turnId) turnIdRef.current = result.turnId;
      if (result.accepted) {
        setSessionNotice("正在停止当前任务…");
      }
    } catch (error) {
      if (error instanceof DataAgentApiError && error.status === 409) return;
      setConnectionError(agentErrorMessage(error));
    }
  }, [requestIdentity, sessionId]);

  const renameConversation = useCallback(
    async (targetSessionId: string, title: string) => {
      if (!requestIdentity) throw new Error("请先登录并选择工作空间");
      const result = await renameAgentSession(
        targetSessionId,
        title,
        requestIdentity,
      );
      dispatch({
        type: "RENAME_CONVERSATION",
        sessionId: targetSessionId,
        title: result.title,
      });
    },
    [requestIdentity],
  );

  const deleteConversation = useCallback(
    async (targetSessionId: string) => {
      if (!requestIdentity) throw new Error("请先登录并选择工作空间");
      await deleteAgentSession(targetSessionId, requestIdentity);
      dispatch({ type: "REMOVE_CONVERSATION", sessionId: targetSessionId });
      if (targetSessionId === sessionId) {
        setPending(null);
        setSessionUsage(EMPTY_USAGE);
      }
    },
    [requestIdentity, sessionId, setPending],
  );

  return {
    conversations: state.conversations,
    activeConversationId,
    activeConversation,
    messages,
    lastMessage,
    isSending,
    isReconnecting,
    isLoading:
      isWorkspaceLoading ||
      isLoadingSessions ||
      isRestoringSession ||
      Boolean(requestIdentityKey && restoredIdentityKey !== requestIdentityKey),
    connectionError,
    sessionNotice,
    sessionUsage,
    pendingConfirmation,
    createConversation,
    selectConversation,
    sendMessage,
    resolveConfirmation,
    stopCurrentTurn,
    renameConversation,
    deleteConversation,
  };
}
