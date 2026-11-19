import type { AgentConversation, AgentMessage, AgentToolCall } from "./types";

export type ConversationState = {
  conversations: AgentConversation[];
  activeConversationId: string | null;
};

export type ConversationAction =
  | { type: "RESET" }
  | { type: "RESTORE_CONVERSATIONS"; conversations: AgentConversation[] }
  | { type: "SELECT_CONVERSATION"; sessionId: string | null }
  | {
      type: "MESSAGE_PAIR_ADDED";
      sessionId: string;
      title: string;
      updatedAt: string;
      userMessage: AgentMessage;
      assistantMessage: AgentMessage;
    }
  | {
      type: "TEXT_DELTA";
      sessionId: string;
      messageId: string;
      delta: string;
      updatedAt: string;
    }
  | {
      type: "TOOL_STARTED";
      sessionId: string;
      messageId: string;
      toolCallId: string;
      toolName: string;
      updatedAt: string;
    }
  | {
      type: "TOOL_COMPLETED";
      sessionId: string;
      messageId: string;
      toolCallId: string;
      updatedAt: string;
    }
  | {
      type: "CONFIRMATION_REQUIRED";
      sessionId: string;
      messageId: string;
      toolCalls: AgentToolCall[];
      updatedAt: string;
    }
  | {
      type: "CONFIRMATION_RESOLVED";
      sessionId: string;
      messageId: string;
      approved: boolean;
      updatedAt: string;
    }
  | {
      type: "MESSAGE_COMPLETED";
      sessionId: string;
      messageId: string;
      updatedAt: string;
    }
  | {
      type: "MESSAGE_FAILED";
      sessionId: string;
      messageId: string;
      error: string;
      updatedAt: string;
    };

export const initialConversationState: ConversationState = {
  conversations: [],
  activeConversationId: null,
};

function updateAssistantMessage(
  state: ConversationState,
  sessionId: string,
  messageId: string,
  updatedAt: string,
  update: (message: AgentMessage) => AgentMessage,
): ConversationState {
  let changed = false;
  const conversations = state.conversations.map((conversation) => {
    if (conversation.id !== sessionId) return conversation;

    let messageChanged = false;
    const messages = conversation.messages.map((message) => {
      if (message.id !== messageId) return message;
      messageChanged = true;
      return update(message);
    });

    if (!messageChanged) return conversation;
    changed = true;
    return { ...conversation, updatedAt, messages };
  });

  return changed ? { ...state, conversations } : state;
}

export function conversationReducer(
  state: ConversationState,
  action: ConversationAction,
): ConversationState {
  switch (action.type) {
    case "RESET":
      return initialConversationState;

    case "RESTORE_CONVERSATIONS": {
      const existingIds = new Set(state.conversations.map((item) => item.id));
      const restored = action.conversations.filter((item) => !existingIds.has(item.id));
      if (restored.length === 0) return state;

      return {
        ...state,
        conversations: [...state.conversations, ...restored].sort(
          (left, right) =>
            new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime(),
        ),
      };
    }

    case "SELECT_CONVERSATION":
      return { ...state, activeConversationId: action.sessionId };

    case "MESSAGE_PAIR_ADDED": {
      const existing = state.conversations.find((item) => item.id === action.sessionId);
      if (!existing) {
        return {
          activeConversationId: action.sessionId,
          conversations: [
            {
              id: action.sessionId,
              title: action.title,
              updatedAt: action.updatedAt,
              messages: [action.userMessage, action.assistantMessage],
            },
            ...state.conversations,
          ],
        };
      }

      return {
        activeConversationId: action.sessionId,
        conversations: state.conversations.map((conversation) =>
          conversation.id === action.sessionId
            ? {
                ...conversation,
                title:
                  conversation.messages.length === 0 ? action.title : conversation.title,
                updatedAt: action.updatedAt,
                restored: false,
                messages: [
                  ...conversation.messages,
                  action.userMessage,
                  action.assistantMessage,
                ],
              }
            : conversation,
        ),
      };
    }

    case "TEXT_DELTA":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => ({
          ...message,
          content: message.content + action.delta,
          status: "streaming",
        }),
      );

    case "TOOL_STARTED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => {
          const calls = message.toolCalls ?? [];
          const exists = calls.some((call) => call.id === action.toolCallId);
          return {
            ...message,
            toolCalls: exists
              ? calls.map((call) =>
                  call.id === action.toolCallId
                    ? { ...call, status: "running" as const }
                    : call,
                )
              : [
                  ...calls,
                  {
                    id: action.toolCallId,
                    name: action.toolName,
                    input: "",
                    status: "running" as const,
                  },
                ],
          };
        },
      );

    case "TOOL_COMPLETED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => ({
          ...message,
          toolCalls: (message.toolCalls ?? []).map((call) =>
            call.id === action.toolCallId
              ? { ...call, status: "completed" as const }
              : call,
          ),
        }),
      );

    case "CONFIRMATION_REQUIRED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => ({
          ...message,
          status: "awaiting-confirmation",
          toolCalls: action.toolCalls.map((call) => ({
            ...call,
            status: "awaiting-confirmation" as const,
          })),
        }),
      );

    case "CONFIRMATION_RESOLVED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => ({
          ...message,
          status: "streaming",
          toolCalls: (message.toolCalls ?? []).map((call) => ({
            ...call,
            status: action.approved ? ("approved" as const) : ("rejected" as const),
          })),
        }),
      );

    case "MESSAGE_COMPLETED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => ({ ...message, status: "complete" }),
      );

    case "MESSAGE_FAILED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => ({ ...message, status: "error", error: action.error }),
      );

    default:
      return state;
  }
}
